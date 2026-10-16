import { Directive, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';

// Un solo observer para toda la landing (antes lo armaba el componente con @ViewChildren('reveal')).
let sharedObserver: IntersectionObserver | undefined;

function getObserver(): IntersectionObserver {
  sharedObserver ??= new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          sharedObserver?.unobserve(entry.target);
        }
      }
    },
    { threshold: 0.2 },
  );
  return sharedObserver;
}

/** Agrega `is-visible` la primera vez que el elemento entra en pantalla. Sin animación si el usuario pide menos movimiento. */
@Directive({ selector: '[appReveal]', standalone: true })
export class Reveal implements OnInit, OnDestroy {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  ngOnInit(): void {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    getObserver().observe(this.el);
  }

  ngOnDestroy(): void {
    sharedObserver?.unobserve(this.el);
  }
}
