import { Component, Input, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { addOutline, removeOutline } from 'ionicons/icons';

addIcons({ 'add-outline': addOutline, 'remove-outline': removeOutline });

/**
 * Numeric stepper (− value +) used in place of a native <input type="number">
 * wherever the value is a bounded quantity (cupo, capacidad, etc.) — native
 * spinner arrows are tiny, inconsistent across browsers, and easy to miss on
 * touch. Implements ControlValueAccessor so it drops into reactive forms via
 * formControlName exactly like ion-input would.
 */
@Component({
  selector: 'app-quantity-stepper',
  imports: [IonIcon],
  templateUrl: './quantity-stepper.html',
  styleUrl: './quantity-stepper.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => QuantityStepper),
      multi: true,
    },
  ],
})
export class QuantityStepper implements ControlValueAccessor {
  @Input() label = '';
  @Input() min = 0;
  @Input() max = 999999;
  @Input() step = 1;

  protected readonly value = signal(0);
  protected readonly disabled = signal(false);

  private onChange: (value: number) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: number): void {
    this.value.set(value ?? this.min);
  }

  registerOnChange(fn: (value: number) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected decrement(): void {
    this.commit(this.value() - this.step);
  }

  protected increment(): void {
    this.commit(this.value() + this.step);
  }

  protected onManualInput(raw: string): void {
    const parsed = Number(raw);
    if (!Number.isNaN(parsed)) {
      this.commit(parsed);
    }
  }

  private commit(next: number): void {
    const clamped = Math.min(this.max, Math.max(this.min, next));
    this.value.set(clamped);
    this.onChange(clamped);
    this.onTouched();
  }
}
