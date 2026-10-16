import { Component, inject } from '@angular/core';
import { Reveal } from '../shared/reveal';
import { ContactModalService } from '../shared/contact-modal.service';
import { ATTENDEES, CLASS_CAPACITY, CLASS_TIME } from '../shared/landing-demo-data';

@Component({
  selector: 'app-landing-booking',
  standalone: true,
  imports: [Reveal],
  templateUrl: './landing-booking.html',
  styleUrl: './landing-booking.scss',
})
export class LandingBooking {
  protected readonly contact = inject(ContactModalService);

  protected readonly classTime = CLASS_TIME;
  protected readonly classCapacity = CLASS_CAPACITY;
  protected readonly attendees = ATTENDEES;
}
