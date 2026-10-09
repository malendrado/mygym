/** Respuesta paginada del backend (ver PageResponse.java) — mismo contrato para todas las listas. */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
}
