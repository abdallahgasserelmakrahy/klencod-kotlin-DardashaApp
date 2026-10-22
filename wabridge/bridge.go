// Package wabridge يربط التطبيق بواتساب كـ«جهاز مصاحب» (Linked Device) عبر مكتبة whatsmeow.
// يُحوَّل إلى مكتبة أندرويد (AAR) بأداة gomobile ويستدعيه التطبيق مباشرة.
//
// ملاحظة: ربط تطبيق غير رسمي بحساب واتساب يخالف شروط واتساب ويحمل خطر الحظر؛ مخصص للاستخدام الشخصي بموافقة أصحاب الأرقام.
package wabridge

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"

	_ "github.com/mattn/go-sqlite3"
	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/store/sqlstore"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	waLog "go.mau.fi/whatsmeow/util/log"
	"google.golang.org/protobuf/proto"
)

// أنواع الرسائل (تطابق WaKind في التطبيق).
const (
	KindText  = 0
	KindImage = 1
	KindAudio = 2
	KindVideo = 3
	KindFile  = 4
)

// Listener تنفّذه طبقة Kotlin لاستقبال الأحداث. تُستدعى من خيوط Go الخلفية.
type Listener interface {
	// OnQR: رمز QR جديد يُعرض للمسح من واتساب (الأجهزة المرتبطة > ربط جهاز).
	OnQR(accountID string, code string)
	// OnPairCode: رمز من 8 خانات يُكتب في واتساب (ربط برقم الهاتف بدلاً من QR).
	OnPairCode(accountID string, code string)
	// OnStatus: connecting | connected | disconnected | logged_out | paired | error. info = رقم الحساب أو رسالة.
	OnStatus(accountID string, status string, info string)
	// OnMessage: رسالة واردة/صادرة. mediaPath فارغ إن لم يوجد مرفق محفوظ.
	OnMessage(accountID string, chatJID string, chatName string, sender string, fromMe bool, tsMillis int64, kind int, text string, mediaPath string, msgID string)
}

type manager struct {
	mu        sync.Mutex
	dir       string
	container *sqlstore.Container
	listener  Listener
	clients   map[string]*whatsmeow.Client
	names     map[string]string // ذاكرة مؤقتة لأسماء المجموعات
	pending   int
}

var m *manager

const maxDownload = 25 << 20 // 25MB

// Start يهيّئ قاعدة البيانات ويعيد توصيل كل الأرقام المرتبطة سابقاً. dataDir = مجلد خاص بالتطبيق.
func Start(dataDir string, l Listener) error {
	if m != nil {
		return nil
	}
	if err := os.MkdirAll(filepath.Join(dataDir, "media"), 0o755); err != nil {
		return err
	}
	store.SetOSInfo("Dardasha", [3]uint32{1, 0, 0})
	ctx := context.Background()
	dsn := "file:" + filepath.Join(dataDir, "wa.db") + "?_foreign_keys=on&_busy_timeout=10000"
	c, err := sqlstore.New(ctx, "sqlite3", dsn, waLog.Noop)
	if err != nil {
		return err
	}
	mg := &manager{dir: dataDir, container: c, listener: l, clients: map[string]*whatsmeow.Client{}, names: map[string]string{}}
	devices, err := c.GetAllDevices(ctx)
	if err != nil {
		return err
	}
	m = mg
	for _, d := range devices {
		if d.ID == nil {
			continue
		}
		id := d.ID.User
		cli := mg.newClient(id, d)
		l.OnStatus(id, "connecting", id)
		go func(cli *whatsmeow.Client, id string) {
			if err := cli.Connect(); err != nil {
				l.OnStatus(id, "error", err.Error())
			}
		}(cli, id)
	}
	return nil
}

// AddAccount يبدأ ربط رقم جديد. phone فارغ → رمز QR؛ وإلا (أرقام فقط بصيغة دولية) → رمز ربط من 8 خانات.
// يعيد معرّف مؤقتاً للحساب قيد الربط، وبعد النجاح يصل OnStatus(مؤقت, "paired", الرقم الحقيقي).
func AddAccount(phone string) (string, error) {
	if m == nil {
		return "", errors.New("not started")
	}
	m.mu.Lock()
	m.pending++
	id := fmt.Sprintf("pending-%d", m.pending)
	m.mu.Unlock()

	phone = strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(phone), "+"))
	dev := m.container.NewDevice()
	cli := m.newClient(id, dev)
	qr, err := cli.GetQRChannel(context.Background())
	if err != nil {
		m.drop(id)
		return "", err
	}
	if err := cli.Connect(); err != nil {
		m.drop(id)
		return "", err
	}
	m.listener.OnStatus(id, "connecting", "")
	go func() {
		pairedPhone := false
		for item := range qr {
			switch item.Event {
			case "code":
				if phone == "" {
					m.listener.OnQR(id, item.Code)
				} else if !pairedPhone {
					pairedPhone = true
					code, err := cli.PairPhone(context.Background(), phone, true, whatsmeow.PairClientChrome, "Chrome (Linux)")
					if err != nil {
						m.listener.OnStatus(id, "error", err.Error())
					} else {
						m.listener.OnPairCode(id, code)
					}
				}
			case "timeout":
				m.listener.OnStatus(id, "error", "انتهت مهلة الربط، حاول مجدداً")
				m.drop(id)
			case "success":
				// يصل PairSuccess من معالج الأحداث
			}
		}
	}()
	return id, nil
}

// Remove يسجّل خروج الرقم (يزيل الجهاز من واتساب) ويحذف بياناته.
func Remove(accountID string) {
	if m == nil {
		return
	}
	m.mu.Lock()
	cli := m.clients[accountID]
	delete(m.clients, accountID)
	m.mu.Unlock()
	if cli == nil {
		return
	}
	ctx := context.Background()
	if cli.IsLoggedIn() {
		_ = cli.Logout(ctx)
	} else {
		cli.Disconnect()
		if cli.Store != nil && cli.Store.ID != nil {
			_ = m.container.DeleteDevice(ctx, cli.Store)
		}
	}
}

// Send يرسل نصاً إلى محادثة (chatJID كما وصلت في OnMessage).
func Send(accountID string, chatJID string, text string) error {
	if m == nil {
		return errors.New("not started")
	}
	m.mu.Lock()
	cli := m.clients[accountID]
	m.mu.Unlock()
	if cli == nil {
		return errors.New("account not connected")
	}
	jid, err := types.ParseJID(chatJID)
	if err != nil {
		return err
	}
	_, err = cli.SendMessage(context.Background(), jid, &waE2E.Message{Conversation: proto.String(text)})
	return err
}

// Stop يقطع كل الاتصالات (دون تسجيل خروج).
func Stop() {
	if m == nil {
		return
	}
	m.mu.Lock()
	for _, c := range m.clients {
		c.Disconnect()
	}
	m.clients = map[string]*whatsmeow.Client{}
	m.mu.Unlock()
	m = nil
}

func (g *manager) drop(id string) {
	g.mu.Lock()
	cli := g.clients[id]
	delete(g.clients, id)
	g.mu.Unlock()
	if cli != nil {
		cli.Disconnect()
	}
}

func (g *manager) newClient(id string, dev *store.Device) *whatsmeow.Client {
	cli := whatsmeow.NewClient(dev, waLog.Noop)
	cli.EnableAutoReconnect = true
	g.mu.Lock()
	g.clients[id] = cli
	g.mu.Unlock()
	// المعرّف يتغير بعد الربط الناجح (من pending-N إلى الرقم)، لذا نقرأه من متغير قابل للتعديل
	cur := id
	cli.AddEventHandler(func(evt interface{}) {
		switch v := evt.(type) {
		case *events.PairSuccess:
			realID := v.ID.User
			g.mu.Lock()
			delete(g.clients, cur)
			g.clients[realID] = cli
			g.mu.Unlock()
			g.listener.OnStatus(cur, "paired", realID)
			cur = realID
		case *events.Connected:
			g.listener.OnStatus(cur, "connected", cur)
		case *events.Disconnected:
			g.listener.OnStatus(cur, "disconnected", "")
		case *events.LoggedOut:
			g.mu.Lock()
			delete(g.clients, cur)
			g.mu.Unlock()
			g.listener.OnStatus(cur, "logged_out", "")
		case *events.Message:
			go g.onMessage(cur, cli, v)
		}
	})
	return cli
}

func (g *manager) chatName(cli *whatsmeow.Client, chat types.JID, pushName string, fromMe bool) string {
	ctx := context.Background()
	if chat.Server == types.GroupServer {
		key := chat.String()
		g.mu.Lock()
		n, ok := g.names[key]
		g.mu.Unlock()
		if ok {
			return n
		}
		if gi, err := cli.GetGroupInfo(ctx, chat); err == nil && gi.Name != "" {
			g.mu.Lock()
			g.names[key] = gi.Name
			g.mu.Unlock()
			return gi.Name
		}
		return "مجموعة"
	}
	if ci, err := cli.Store.Contacts.GetContact(ctx, chat.ToNonAD()); err == nil {
		if ci.FullName != "" {
			return ci.FullName
		}
		if ci.PushName != "" {
			return ci.PushName
		}
		if ci.BusinessName != "" {
			return ci.BusinessName
		}
	}
	if !fromMe && pushName != "" {
		return pushName
	}
	return chat.User
}

func (g *manager) onMessage(acc string, cli *whatsmeow.Client, ev *events.Message) {
	msg := ev.Message
	if msg == nil {
		return
	}
	kind, text, ext := KindText, "", ""
	var dl whatsmeow.DownloadableMessage
	var size uint64

	switch {
	case msg.GetConversation() != "":
		text = msg.GetConversation()
	case msg.GetExtendedTextMessage() != nil:
		text = msg.GetExtendedTextMessage().GetText()
	case msg.GetImageMessage() != nil:
		im := msg.GetImageMessage()
		kind, ext, dl, size = KindImage, "jpg", im, im.GetFileLength()
		text = im.GetCaption()
		if text == "" {
			text = "📷 صورة"
		} else {
			text = "📷 " + text
		}
	case msg.GetStickerMessage() != nil:
		st := msg.GetStickerMessage()
		kind, ext, dl, size, text = KindImage, "webp", st, st.GetFileLength(), "🖼 ملصق"
	case msg.GetAudioMessage() != nil:
		au := msg.GetAudioMessage()
		kind, ext, dl, size = KindAudio, "ogg", au, au.GetFileLength()
		text = fmt.Sprintf("🎤 رسالة صوتية (%d:%02d)", au.GetSeconds()/60, au.GetSeconds()%60)
	case msg.GetVideoMessage() != nil:
		vi := msg.GetVideoMessage()
		kind, ext, dl, size = KindVideo, "mp4", vi, vi.GetFileLength()
		text = vi.GetCaption()
		if text == "" {
			text = "🎥 فيديو"
		} else {
			text = "🎥 " + text
		}
	case msg.GetDocumentMessage() != nil:
		kind = KindFile
		text = "📄 " + msg.GetDocumentMessage().GetFileName()
	default:
		return // أنواع غير مدعومة (تفاعلات، استطلاعات...) تُتجاهل في هذه المرحلة
	}
	if text == "" && dl == nil {
		return
	}

	path := ""
	if dl != nil && size > 0 && size <= maxDownload {
		if data, err := cli.Download(context.Background(), dl); err == nil && len(data) > 0 {
			p := filepath.Join(g.dir, "media", safeName(ev.Info.ID)+"."+ext)
			if os.WriteFile(p, data, 0o600) == nil {
				path = p
			}
		}
	}

	chat := ev.Info.Chat
	g.listener.OnMessage(
		acc, chat.String(), g.chatName(cli, chat, ev.Info.PushName, ev.Info.IsFromMe),
		ev.Info.PushName, ev.Info.IsFromMe, ev.Info.Timestamp.UnixMilli(),
		kind, text, path, ev.Info.ID,
	)
}

func safeName(s string) string {
	return strings.Map(func(r rune) rune {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') {
			return r
		}
		return '_'
	}, s)
}
