#!/bin/bash
# Deploy de la landing a Vercel — incluye SIEMPRE el brochure estático (vive en brochure-src/,
# fuera del build de Angular; un `ng build` + copiar dist/ a public/ solo, sin este paso,
# lo borra sin avisar porque public/ es puro output regenerado, nunca la fuente de verdad).
set -e

cd "$(dirname "$0")/.."
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
