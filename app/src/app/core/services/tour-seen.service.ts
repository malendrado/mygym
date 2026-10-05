import { Injectable, signal } from '@angular/core';

// Mismo patrón que PwaInstallService: localStorage con lectura defensiva (modo privado puede
// tirar o simplemente no persistir, nunca debe romper la página). Esto NO es el tracking real
// por paso (eso vive en el backend, ver TourService/GymService.recordTourStep) — solo decide si
// destacar el botón "Ver tour" la primera vez que un DEMO_ADMIN entra al panel.
const ADMIN_SEEN_KEY = 'mygym.adminTourSeen';
const MEMBER_SEEN_KEY = 'mygym.memberTourSeen';

@Injectable({ providedIn: 'root' })
export class TourSeenService {
  readonly adminTourSeen = signal(readFlag(ADMIN_SEEN_KEY));
  readonly memberTourSeen = signal(readFlag(MEMBER_SEEN_KEY));

  markAdminTourSeen(): void {
    this.adminTourSeen.set(true);
    writeFlag(ADMIN_SEEN_KEY);
  }

  markMemberTourSeen(): void {
    this.memberTourSeen.set(true);
    writeFlag(MEMBER_SEEN_KEY);
  }
}

function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

function writeFlag(key: string): void {
  try {
    localStorage.setItem(key, '1');
  } catch {
    // Modo privado: el botón simplemente se sigue destacando la próxima vez, no rompe nada.
  }
}
