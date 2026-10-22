# wabridge

جسر Go (whatsmeow) يُبنى تلقائياً في GitHub Actions إلى `app/libs/wabridge.aar`:

    go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
    cd wabridge && go get go.mau.fi/whatsmeow@latest github.com/mattn/go-sqlite3 golang.org/x/mobile/bind && go mod tidy
    gomobile bind -target=android/arm64,android/arm -androidapi 26 -o ../app/libs/wabridge.aar .
