import { Component, inject } from '@angular/core';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeOutline, downloadOutline, shareOutline } from 'ionicons/icons';
import { PwaInstallService } from '../../../core/services/pwa-install.service';

@Component({
  selector: 'app-install-banner',
  imports: [IonButton, IonIcon],
  templateUrl: './install-banner.html',
  styleUrl: './install-banner.scss',
})
export class InstallBanner {
  protected readonly pwa = inject(PwaInstallService);

  constructor() {
    addIcons({ closeOutline, downloadOutline, shareOutline });
  }
}
