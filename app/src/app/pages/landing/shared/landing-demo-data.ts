export interface Attendee {
  initials: string;
  name: string;
}

// Misma clase de ejemplo en el widget del hero y en la tarjeta "Así reservan tus socios".
export const CLASS_TIME = '18:30 - 20:00';
export const CLASS_CAPACITY = { taken: 7, total: 10 };
export const ATTENDEES: Attendee[] = [
  { initials: 'CP', name: 'Constanza Pérez' },
  { initials: 'DG', name: 'Dayana Guerra' },
  { initials: 'JP', name: 'Javiera Pulgar' },
  { initials: 'MU', name: 'Makarena Ubeda' },
];
