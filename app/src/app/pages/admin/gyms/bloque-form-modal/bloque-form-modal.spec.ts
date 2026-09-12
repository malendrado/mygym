import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BloqueFormModal } from './bloque-form-modal';
import { GymBlock } from '../../../../core/models/gym.model';

describe('BloqueFormModal', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BloqueFormModal],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(BloqueFormModal);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('is invalid when required fields are empty', () => {
    const fixture = TestBed.createComponent(BloqueFormModal);
    fixture.detectChanges();
    expect(fixture.componentInstance['form'].invalid).toBe(true);
  });

  it('is invalid when endTime is not after startTime', () => {
    const fixture = TestBed.createComponent(BloqueFormModal);
    fixture.detectChanges();
    const form = fixture.componentInstance['form'];
    form.setValue({ label: 'Morning', dayOfWeek: 'MONDAY', startTime: '10:00', endTime: '09:00', capacity: 10 });
    expect(form.invalid).toBe(true);
    expect(form.errors?.['endBeforeStart']).toBe(true);
  });

  it('emits the create payload on valid submit', () => {
    const fixture = TestBed.createComponent(BloqueFormModal);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    let emitted: unknown;
    component.saved.subscribe((value) => (emitted = value));

    component['form'].setValue({
      label: 'Morning',
      dayOfWeek: 'MONDAY',
      startTime: '08:00',
      endTime: '09:00',
      capacity: 20,
    });
    component['submit']();

    expect(emitted).toEqual({
      label: 'Morning',
      dayOfWeek: 'MONDAY',
      startTime: '08:00',
      endTime: '09:00',
      capacity: 20,
    });
  });

  it('emits the update payload (including active) when editing an existing block', () => {
    const fixture = TestBed.createComponent(BloqueFormModal);
    const component = fixture.componentInstance;
    const existing: GymBlock = {
      id: 1,
      gymId: 1,
      label: 'Morning',
      dayOfWeek: 'MONDAY',
      startTime: '08:00',
      endTime: '09:00',
      capacity: 20,
      active: false,
    };
    component.block = existing;
    fixture.detectChanges();

    let emitted: unknown;
    component.saved.subscribe((value) => (emitted = value));
    component['submit']();

    expect(emitted).toEqual({
      label: 'Morning',
      dayOfWeek: 'MONDAY',
      startTime: '08:00',
      endTime: '09:00',
      capacity: 20,
      active: false,
    });
  });
});
