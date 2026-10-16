import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { VersionTag } from '../../../core/components/version-tag/version-tag';
import { Reveal } from '../shared/reveal';
import { ContactModalService } from '../shared/contact-modal.service';

@Component({
  selector: 'app-landing-footer',
  standalone: true,
  imports: [RouterLink, VersionTag, Reveal],
  templateUrl: './landing-footer.html',
  styleUrl: './landing-footer.scss',
})
export class LandingFooter {
  protected readonly contact = inject(ContactModalService);
}
