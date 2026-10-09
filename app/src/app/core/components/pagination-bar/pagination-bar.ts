import { Component, computed, input, output } from '@angular/core';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { chevronBackOutline, chevronForwardOutline } from 'ionicons/icons';

addIcons({
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
});

/**
 * Controles de páginas numeradas (Anterior · Página X de Y · Siguiente) para tablas de gestión
 * del admin — ver la decisión de UX en la paginación de mygym (feeds usan "Cargar más", tablas
 * de gestión usan páginas). `page` es 0-indexado; se muestra 1-indexado.
 */
@Component({
  selector: 'app-pagination-bar',
  imports: [IonButton, IonIcon],
  templateUrl: './pagination-bar.html',
  styleUrl: './pagination-bar.scss',
})
export class PaginationBar {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly loading = input(false);
  /** Sustantivo plural para el resumen: "25 socios", "12 gimnasios"... */
  readonly itemLabel = input('resultados');
  readonly pageChange = output<number>();

  protected readonly isFirst = computed(() => this.page() <= 0);
  protected readonly isLast = computed(() => this.page() >= this.totalPages() - 1);

  protected go(target: number): void {
    if (target < 0 || target > this.totalPages() - 1 || target === this.page()) {
      return;
    }
    this.pageChange.emit(target);
  }
}
