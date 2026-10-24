// Solo páginas públicas: /activar/:token, /guia/:token y /checkin/:code llevan secretos en la URL y no deben llegar a Google.
if (/^\/(faq|privacidad)?\/?$/.test(location.pathname)) {
  var s = document.createElement('script');
  s.async = true;
  s.src = 'https://www.googletagmanager.com/gtag/js?id=G-R1V6X7TVK2';
  document.head.appendChild(s);
  window.dataLayer = window.dataLayer || [];
  window.gtag = function () { dataLayer.push(arguments); };
  gtag('js', new Date());
  // Un referrer del mismo sitio podría traer una URL con token (/activar/..): solo se envía el origen.
  gtag('config', 'G-R1V6X7TVK2', { page_referrer: document.referrer.indexOf(location.origin) === 0 ? location.origin : document.referrer });
  // Microsoft Clarity (grabaciones y mapas de calor), con la misma restricción de páginas.
  (function (c, l, a, r, i, t, y) {
    c[a] = c[a] || function () { (c[a].q = c[a].q || []).push(arguments); };
    t = l.createElement(r); t.async = 1; t.src = 'https://www.clarity.ms/tag/' + i;
    y = l.getElementsByTagName(r)[0]; y.parentNode.insertBefore(t, y);
  })(window, document, 'clarity', 'script', 'z2ptssf8lq');
}
