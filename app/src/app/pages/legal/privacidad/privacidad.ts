import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonContent } from '@ionic/angular';
import { scrollToSection } from '../scroll-to-section';

@Component({
  selector: 'app-legal-privacidad',
  imports: [RouterLink, IonContent],
  templateUrl: './privacidad.html',
  styleUrl: '../legal-page.scss',
})
export class LegalPrivacidad {
  protected readonly goTo = scrollToSection;
}
