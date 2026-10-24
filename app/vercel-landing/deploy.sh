#!/bin/bash
# Deploy de la landing a Vercel — incluye SIEMPRE el brochure estático (vive en brochure-src/,
# fuera del build de Angular; un `ng build` + copiar dist/ a public/ solo, sin este paso,
# lo borra sin avisar porque public/ es puro output regenerado, nunca la fuente de verdad).
set -e

cd "$(dirname "$0")/.."

# 0) Número de versión visible en la app (src/environments/build-info.ts): fecha de Chile + commit
# corto actual. "dirty" = el código que se despliega tiene cambios sin commitear: archivos VERSIONADOS
# modificados (sin contar este mismo archivo, que se reescribe acá, ni logos/) o archivos NUEVOS sin
# versionar dentro de app/src o api/src. Los archivos sueltos de otras carpetas (docs/, .angular/, etc.,
# p. ej. de otra sesión de trabajo) no entran en el build y ya no marcan "dirty".
# Se restaura al final para que git no lo vea modificado.
BUILD_FILE="$(pwd)/src/environments/build-info.ts"
BUILD_COMMIT="$(git rev-parse --short HEAD)"
BUILD_DATE="$(TZ=America/Santiago date +%Y.%m.%d)"
if [ -n "$(git status --porcelain --untracked-files=no -- ':(top)' ':(exclude,top)app/src/environments/build-info.ts' ':(exclude,top)logos' | head -1)" ] \
  || [ -n "$(git ls-files --others --exclude-standard -- ':(top)app/src' ':(top)api/src' | head -1)" ]; then
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

# La CSP de vercel.json permite el unico manejador inline que genera Angular (onload del <link> de estilos) por su hash. Si una
# actualizacion de Angular lo cambia, la pagina quedaria SIN estilos: se corta el deploy antes de subirla.
node -e "
const fs = require('fs'), c = require('crypto');
const html = fs.readFileSync('dist/landing/browser/index.html', 'utf8');
const hashes = [...html.matchAll(/\son(?:load|error|click)=\"([^\"]*)\"/g)].map((m) => 'sha256-' + c.createHash('sha256').update(m[1]).digest('base64'));
const vercel = fs.readFileSync('vercel-landing/vercel.json', 'utf8');
const missing = hashes.filter((h) => !vercel.includes(h));
if (missing.length) { console.error('La CSP de vercel.json no incluye el hash del manejador inline del build (' + missing.join(', ') + '). Actualiza vercel.json antes de desplegar.'); process.exit(1); }
console.log('CSP al dia (' + hashes.length + ' hash del build presentes en vercel.json)');
"

cd vercel-landing
echo "2/4 — regenerando public/ desde el build..."
rm -rf public
cp -r ../dist/landing/browser public

echo "3/4 — restaurando el brochure estático..."
mkdir -p public/brochure
cp brochure-src/index.html public/brochure/index.html
cp brochure-src/og-image.png public/brochure/og-image.png
cp brochure-src/og-image.jpg public/brochure/og-image.jpg

echo "4/4 — deploy a Vercel..."
npx vercel --prod --yes --scope gvegasc-1191s-projects
