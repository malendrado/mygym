import { Component } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { pulseOutline } from 'ionicons/icons';
import { Reveal } from '../shared/reveal';

addIcons({ 'pulse-outline': pulseOutline });

@Component({
  selector: 'app-landing-story',
  standalone: true,
  imports: [IonIcon, Reveal],
  templateUrl: './landing-story.html',
  styleUrl: './landing-story.scss',
})
export class LandingStory {}
