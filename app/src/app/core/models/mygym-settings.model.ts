/** Datos de mygym como empresa (panel de super admin). Lo que llega es lo vigente: lo guardado o, si falta, el valor de respaldo del servidor. */
export interface MygymSettings {
  whatsapp: string;
  contactEmail: string;
  companyName: string;
  companyRut: string;
  legalRepName: string;
  legalRepRut: string;
  companyAddress: string;
  bankHolder: string;
  bankRut: string;
  bankName: string;
  bankAccountType: string;
  bankAccountNumber: string;
  bankEmail: string;
  updatedAt: string | null;
}

export type MygymSettingsRequest = Omit<MygymSettings, 'updatedAt'>;

/** Contacto público de mygym: solo WhatsApp y correo. */
export interface MygymContact {
  whatsapp: string;
  email: string;
}
