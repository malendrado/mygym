import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../../../../core/services/auth.service';
import { MemberService } from '../../../../core/services/member.service';
import { ImportMembersModal } from './import-members-modal';

type Internals = {
  rows: { set: (rows: unknown[]) => void };
  includedCount: () => number;
  willImportCount: () => number;
  overLimit: () => boolean;
};

function create(freeSlots: number | null, validRows: number): Internals {
  TestBed.configureTestingModule({
    providers: [
      { provide: MemberService, useValue: {} },
      { provide: AuthService, useValue: { currentUser: () => ({ role: 'GYM_ADMIN' }) } },
    ],
  });
  const modal = TestBed.runInInjectionContext(() => new ImportMembersModal());
  modal.freeSlots = freeSlots;
  const internals = modal as unknown as Internals;
  internals.rows.set(
    Array.from({ length: validRows }, (_, i) => ({ rowNumber: i + 2, status: 'ok', included: true, email: `s${i}@x.cl`, name: `S${i}` })),
  );
  return internals;
}

describe('ImportMembersModal - cupo del gimnasio', () => {
  it('with room for everything the preview is unchanged', () => {
    const internals = create(20, 10);
    expect(internals.overLimit()).toBe(false);
    expect(internals.willImportCount()).toBe(10);
  });

  it('a spreadsheet bigger than the free slots only loads as many rows as fit, and says so', () => {
    const internals = create(3, 10);
    expect(internals.includedCount()).toBe(10);
    expect(internals.overLimit()).toBe(true);
    expect(internals.willImportCount()).toBe(3);
  });

  it('exactly as many rows as slots is not a problem', () => {
    const internals = create(10, 10);
    expect(internals.overLimit()).toBe(false);
    expect(internals.willImportCount()).toBe(10);
  });

  it('a full gym imports nothing', () => {
    const internals = create(0, 5);
    expect(internals.overLimit()).toBe(true);
    expect(internals.willImportCount()).toBe(0);
  });

  it('when the limit is unknown nothing is restricted', () => {
    const internals = create(null, 7);
    expect(internals.overLimit()).toBe(false);
    expect(internals.willImportCount()).toBe(7);
  });
});
