// Valor por defecto para desarrollo. vercel-landing/deploy.sh lo REESCRIBE justo antes de compilar
// con la fecha y el commit reales (y lo restaura después, para que git no lo vea modificado).
export const BUILD_INFO = {
  commit: 'dev',
  date: 'dev',
  dirty: false,
};
