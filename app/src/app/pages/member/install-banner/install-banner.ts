import { Component, inject } from '@angular/core';
import { IonButton, IonIcon, IonPopover } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { addCircleOutline, arrowForwardOutline, chevronDownOutline, downloadOutline, shareOutline } from 'ionicons/icons';
import { PwaInstallService } from '../../../core/services/pwa-install.service';

// Pedido explícito del usuario: nada de banner grande fijo arriba de todo — un ícono chico en
// el header (al lado de "Salir"), visible y accesible, para quien SÍ quiera instalar. En
// Android dispara el instalador nativo directo; en iOS (sin esa API) abre un popover con los
// mismos 2 pasos que antes vivían siempre visibles en el banner.
@Component({
  selector: 'app-install-banner',
  imports: [IonButton, IonIcon, IonPopover],
  templateUrl: './install-banner.html',
  styleUrl: './install-banner.scss',
})
export class InstallBanner {
  protected readonly pwa = inject(PwaInstallService);

  constructor() {
    addIcons({ addCircleOutline, arrowForwardOutline, chevronDownOutline, downloadOutline, shareOutline });
  }
}
