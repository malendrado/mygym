import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonContent } from '@ionic/angular';
import { MygymContactService } from '../../../core/services/mygym-contact.service';
import { scrollToSection } from '../scroll-to-section';

@Component({
  selector: 'app-legal-privacidad',
  imports: [RouterLink, IonContent],
  templateUrl: './privacidad.html',
  styleUrl: '../legal-page.scss',
})
export class LegalPrivacidad {
  protected readonly goTo = scrollToSection;
  protected readonly contact = inject(MygymContactService);
}
