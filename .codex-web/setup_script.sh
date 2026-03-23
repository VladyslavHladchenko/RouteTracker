set -euxo pipefail

GRADLE_VERSION=9.3.1
INSTALL_ROOT="$HOME/.local"
GRADLE_HOME_DIR="$INSTALL_ROOT/gradle-$GRADLE_VERSION"

mkdir -p "$INSTALL_ROOT"

if [ ! -x "$GRADLE_HOME_DIR/bin/gradle" ]; then
  curl -fL --retry 3 \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    -o /tmp/gradle.zip
  rm -rf "$GRADLE_HOME_DIR"
  unzip -q /tmp/gradle.zip -d "$INSTALL_ROOT"
fi

echo "export GRADLE_HOME=\"$GRADLE_HOME_DIR\"" >> ~/.bashrc
echo "export PATH=\"$GRADLE_HOME_DIR/bin:\$PATH\"" >> ~/.bashrc

"$GRADLE_HOME_DIR/bin/gradle" --version
java -version
