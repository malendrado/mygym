import { DayOfWeek } from './gym.model';

export interface GymBlockOccurrence {
  gymBlockId: number;
  label: string;
  classDate: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  capacity: number;
  category: string | null;
  instructorName: string | null;
  instructorPhoto: string | null;
  taken: number;
  bookable: boolean;
  past: boolean;
  myReservationId: number | null;
}

export type ReservationStatus = 'BOOKED' | 'CANCELLED';

export interface Reservation {
  id: number;
  gymBlockId: number;
  blockLabel: string;
  classDate: string;
  startTime: string;
  endTime: string;
  status: ReservationStatus;
  createdAt: string;
}

export interface CreateReservationRequest {
  gymBlockId: number;
  classDate: string;
}
