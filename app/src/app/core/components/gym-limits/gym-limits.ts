import { Component, Input, computed, signal } from '@angular/core';
import { GymLimits, LimitUsage } from '../../models/gym.model';

interface LimitRow {
  key: 'members' | 'profesores' | 'admins';
  name: string;
  usage: LimitUsage;
  percent: number;
  state: 'ok' | 'near' | 'full';
  hint: string;
}

/**
 * Cuántos cupos usa el gimnasio y cuántos le quedan (socios, profesores, administradores). Lo ven el administrador del gimnasio
 * y el super-admin. El estado nunca va solo por color: siempre hay un texto ("Te quedan 3", "Cupo completo").
 */
@Component({
  selector: 'app-gym-limits',
  templateUrl: './gym-limits.html',
  styleUrl: './gym-limits.scss',
})
export class GymLimitsPanel {
  private readonly _limits = signal<GymLimits | null>(null);
  private readonly _audience = signal<'admin' | 'super'>('admin');

  @Input() set limits(value: GymLimits | null) {
    this._limits.set(value);
  }

  /** `admin`: dueño del gimnasio (para ampliar el cupo debe escribirle a mygym). `super`: puede cambiarlo él mismo. */
  @Input() set audience(value: 'admin' | 'super') {
    this._audience.set(value);
  }

  // Título de la sección: para el super admin habla "de este gimnasio" (no es su gym).
  protected readonly sectionTitle = computed(() =>
    this._audience() === 'super' ? 'Cupos de este gimnasio' : 'Cupos de tu gimnasio',
  );

  /** Una sola línea (solo socios), para ponerla junto a "Agregar" e "Importar". */
  @Input() compact = false;

  protected readonly rows = computed<LimitRow[]>(() => {
    const limits = this._limits();
    if (!limits) {
      return [];
    }
    const audience = this._audience();
    const row = (key: LimitRow['key'], name: string, usage: LimitUsage, noun: string, fullHint: string): LimitRow => {
      const percent = usage.max > 0 ? Math.min(100, Math.round((usage.used / usage.max) * 100)) : 100;
      const state: LimitRow['state'] = usage.free === 0 ? 'full' : percent >= 90 ? 'near' : 'ok';
      const hint =
        state === 'full'
          ? fullHint
          : usage.free === 1
            ? `Te queda 1 ${noun}`
            : `Te quedan ${usage.free} ${noun}s`;
      return { key, name, usage, percent, state, hint };
    };
    const membersFull =
      audience === 'super'
        ? 'Cupo completo · puedes ampliarlo en Configuración'
        : 'Cupo completo · escríbenos a contacto@mygym.cl para ampliarlo';
    return [
      row('members', 'Socios', limits.members, 'cupo', membersFull),
      row('profesores', 'Profesores', limits.profesores, 'cupo', 'Llegaste al máximo de profesores'),
      row('admins', 'Administradores', limits.admins, 'cupo', 'Llegaste al máximo de administradores'),
    ];
  });

  protected readonly members = computed(() => this.rows().find((r) => r.key === 'members') ?? null);
}
