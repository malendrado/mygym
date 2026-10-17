import { Component, OnDestroy, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { IonContent } from '@ionic/angular';

/**
 * Comodín de las rutas: antes cualquier URL escrita mal redirigía en silencio a la landing o al login. El hosting
 * responde 200 con index.html para todo (no se puede devolver un 404 real desde Angular), así que al menos se avisa y se
 * marca la página como noindex mientras está abierta.
 */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, IonContent],
  templateUrl: './not-found.html',
  styleUrl: './not-found.scss',
})
export class NotFoundPage implements OnDestroy {
  private readonly meta = inject(Meta);
  private readonly robotsTag: HTMLMetaElement | null;

  constructor() {
    this.robotsTag = this.meta.addTag({ name: 'robots', content: 'noindex' }, true) ?? null;
  }

  ngOnDestroy(): void {
    this.meta.removeTagElement(this.robotsTag as HTMLMetaElement);
  }
}
