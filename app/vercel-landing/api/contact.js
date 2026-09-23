const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CONTACT_TO = 'contacto@mygym.cl';
const CONTACT_FROM = 'mygym <notificaciones@cortesdev.cl>';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.status(405).json({ ok: false, error: 'Método no permitido.' });
    return;
  }

  const { name, email, message } = req.body ?? {};

  if (typeof name !== 'string' || !name.trim()) {
    res.status(400).json({ ok: false, error: 'Falta el nombre.' });
    return;
  }
  if (typeof email !== 'string' || !EMAIL_PATTERN.test(email.trim())) {
    res.status(400).json({ ok: false, error: 'Email inválido.' });
    return;
  }

  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    console.error('RESEND_API_KEY no está configurada.');
    res.status(500).json({ ok: false, error: 'El envío de correo no está configurado.' });
    return;
  }

  try {
    const resendResponse = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        from: CONTACT_FROM,
        to: [CONTACT_TO],
        reply_to: email.trim(),
        subject: `Consulta desde mygym de ${name.trim()}`,
        text: [
          `Nombre: ${name.trim()}`,
          `Email: ${email.trim()}`,
          '',
          typeof message === 'string' && message.trim() ? message.trim() : '(sin mensaje)',
        ].join('\n'),
      }),
    });

    if (!resendResponse.ok) {
      const errorBody = await resendResponse.text();
      console.error('Resend respondió con error', resendResponse.status, errorBody);
      res.status(502).json({ ok: false, error: 'No se pudo enviar el correo.' });
      return;
    }

    res.status(200).json({ ok: true });
  } catch (error) {
    console.error('Error llamando a Resend', error);
    res.status(500).json({ ok: false, error: 'Error interno enviando el correo.' });
  }
}
