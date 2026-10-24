const PLANS_URL = 'https://api-production-be95.up.railway.app/api/public/mygym-plans';

const clp = (n) => '$' + n.toLocaleString('es-CL');

// Texto plano para buscadores con IA que no ejecutan JavaScript. Se arma en cada consulta (caché de 60 s en el borde),
// así un cambio de plan en el panel se refleja sin desplegar.
export default async function handler(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.status(405).send('Método no permitido.');
    return;
  }

  let plans;
  try {
    const response = await fetch(PLANS_URL, { signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    plans = await response.json();
  } catch (error) {
    console.error('No se pudieron leer los planes', error);
    res.setHeader('Cache-Control', 'no-store');
    res.status(502).send('Planes no disponibles por ahora.');
    return;
  }

  if (!Array.isArray(plans) || plans.length === 0) {
    res.setHeader('Cache-Control', 'no-store');
    res.status(502).send('Planes no disponibles por ahora.');
    return;
  }

  const lines = [
    '# Planes de mygym',
    '',
    'Software para gimnasios y boxes. Todos los planes incluyen todas las funciones y se diferencian por la cantidad de socios. Sin comisión por socio ni por pago.',
    '',
  ];
  for (const plan of plans.filter((p) => p.active !== false).sort((a, b) => a.sortOrder - b.sortOrder)) {
    lines.push(`## ${plan.name}`, `- ${plan.clientsLabel}`);
    lines.push(`- Precio: ${plan.priceClp ? `${clp(plan.priceClp)} al mes + IVA` : 'a medida, escríbenos'}`);
    for (const feature of plan.features ?? []) lines.push(`- ${feature}`);
    lines.push('');
  }

  res.setHeader('Content-Type', 'text/markdown; charset=utf-8');
  res.setHeader('Cache-Control', 'public, s-maxage=60, stale-while-revalidate=300');
  res.status(200).send(lines.join('\n'));
}
