import { DOCUMENT } from '@angular/common';
import { inject, provideAppInitializer } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * La app es una sola página HTML para todas las rutas, así que el canonical y la descripción del index
 * quedaban iguales en /faq y /privacidad (Google las tomaba por copias de la home). Las rutas que
 * declaran `data: { description }` obtienen canonical y descripción propios; el resto vuelve a los del index.
 */
export function provideRouteSeo() {
  return provideAppInitializer(() => {
    const doc = inject(DOCUMENT);
    const router = inject(Router);
    const canonical = doc.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    const description = doc.querySelector<HTMLMetaElement>('meta[name="description"]');
    const defaultCanonical = canonical?.href ?? '';
    const defaultDescription = description?.content ?? '';

    router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe((e) => {
      let route = router.routerState.snapshot.root;
      while (route.firstChild) route = route.firstChild;
      const own = route.data['description'] as string | undefined;
      if (canonical) canonical.href = own ? doc.location.origin + e.urlAfterRedirects.split(/[?#]/)[0] : defaultCanonical;
      if (description) description.content = own ?? defaultDescription;
    });
  });
}
