import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, inject } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { arrowForwardOutline } from 'ionicons/icons';
import { ContactModalService } from '../shared/contact-modal.service';
import { ATTENDEES, CLASS_CAPACITY, CLASS_TIME } from '../shared/landing-demo-data';

addIcons({ 'arrow-forward-outline': arrowForwardOutline });

@Component({
  selector: 'app-landing-hero',
  standalone: true,
  imports: [IonIcon],
  templateUrl: './landing-hero.html',
  styleUrl: './landing-hero.scss',
})
export class LandingHero implements AfterViewInit, OnDestroy {
  protected readonly contact = inject(ContactModalService);

  @ViewChild('heroCta') protected heroCtaRef?: ElementRef<HTMLElement>;

  private magneticCtaCleanup?: () => void;

  protected readonly classTime = CLASS_TIME;
  protected readonly classCapacity = CLASS_CAPACITY;
  protected readonly attendees = ATTENDEES;

  ngAfterViewInit(): void {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    this.setupMagneticCta();
  }

  private setupMagneticCta(): void {
    const button = this.heroCtaRef?.nativeElement;
    if (!button) {
      return;
    }

    const onPointerMove = (event: PointerEvent): void => {
      const rect = button.getBoundingClientRect();
      const x = (event.clientX - rect.left - rect.width / 2) * 0.3;
      const y = (event.clientY - rect.top - rect.height / 2) * 0.3;
      button.style.transform = `translate(${x}px, ${y}px)`;
    };

    const onPointerLeave = (): void => {
      button.style.transform = '';
    };

    button.addEventListener('pointermove', onPointerMove);
    button.addEventListener('pointerleave', onPointerLeave);
    this.magneticCtaCleanup = () => {
      button.removeEventListener('pointermove', onPointerMove);
      button.removeEventListener('pointerleave', onPointerLeave);
    };
  }

  ngOnDestroy(): void {
    this.magneticCtaCleanup?.();
  }
}
