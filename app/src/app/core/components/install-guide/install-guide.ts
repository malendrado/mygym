import { Component, inject, signal } from '@angular/core';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { downloadOutline, logoAndroid, logoApple } from 'ionicons/icons';
import { PwaInstallService } from '../../services/pwa-install.service';

addIcons({ 'download-outline': downloadOutline, 'logo-apple': logoApple, 'logo-android': logoAndroid });

type Platform = 'iphone' | 'android';

/**
 * Instrucciones para instalar mygym como app en el teléfono, pensadas para el ADMIN del gimnasio
 * (el socio tiene su propio ícono "Instala tu app" en /member, ver InstallBanner). Se muestra solo
 * si la página NO corre ya como app instalada. La pestaña inicial se elige según el dispositivo.
 * En Android con instalador nativo disponible, además ofrece "Instalar ahora" directo.
 */
@Component({
  selector: 'app-install-guide',
  imports: [IonButton, IonIcon],
  templateUrl: './install-guide.html',
  styleUrl: './install-guide.scss',
})
export class InstallGuide {
  protected readonly pwa = inject(PwaInstallService);

  protected readonly platform = signal<Platform>(
    /iphone|ipad|ipod/i.test(navigator.userAgent) ? 'iphone' : 'android',
  );

  protected select(platform: Platform): void {
    this.platform.set(platform);
  }
}
