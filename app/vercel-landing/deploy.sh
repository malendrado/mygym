#!/bin/bash
# Deploy de la landing a Vercel — incluye SIEMPRE el brochure estático (vive en brochure-src/,
# fuera del build de Angular; un `ng build` + copiar dist/ a public/ solo, sin este paso,
# lo borra sin avisar porque public/ es puro output regenerado, nunca la fuente de verdad).
set -e

cd "$(dirname "$0")/.."

# 0) Número de versión visible en la app (src/environments/build-info.ts): fecha de Chile + commit
# corto actual. "dirty" = hay cambios sin commitear (sin contar este mismo archivo, que se
# reescribe acá, ni logos/). Se restaura al final para que git no lo vea modificado.
BUILD_FILE="$(pwd)/src/environments/build-info.ts"
BUILD_COMMIT="$(git rev-parse --short HEAD)"
BUILD_DATE="$(TZ=America/Santiago date +%Y.%m.%d)"
if [ -n "$(git status --porcelain -- ':(top)' ':(exclude,top)app/src/environments/build-info.ts' ':(exclude,top)logos' | head -1)" ]; then
  BUILD_DIRTY="true"
else
  BUILD_DIRTY="false"
fi
trap 'git checkout -- "$BUILD_FILE" 2>/dev/null || true' EXIT
printf "export const BUILD_INFO = {\n  commit: '%s',\n  date: '%s',\n  dirty: %s,\n};\n" \
  "$BUILD_COMMIT" "$BUILD_DATE" "$BUILD_DIRTY" > "$BUILD_FILE"
echo "Versión: v$BUILD_DATE · $BUILD_COMMIT (dirty=$BUILD_DIRTY)"

echo "1/4 — build de Angular (landing)..."
npx ng build landing

cd vercel-landing
echo "2/4 — regenerando public/ desde el build..."
rm -rf public
cp -r ../dist/landing/browser public

echo "3/4 — restaurando el brochure estático..."
mkdir -p public/brochure
cp brochure-src/index.html public/brochure/index.html
cp brochure-src/og-image.png public/brochure/og-image.png

echo "4/4 — deploy a Vercel..."
npx vercel --prod --yes --scope gvegasc-1191s-projects
