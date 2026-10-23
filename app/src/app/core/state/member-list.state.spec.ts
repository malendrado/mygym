import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { Member, MemberPageQuery, MemberSummary } from '../models/member.model';
import { Page } from '../models/page.model';
import { MemberListState } from './member-list.state';

const member = (id: number) => ({ id, name: `Socio ${id}` }) as unknown as Member;
const page = (ids: number[], p = 0, total = ids.length): Page<Member> => ({ items: ids.map(member), page: p, size: 25, totalElements: total, hasNext: false });
const summary = {} as MemberSummary;

function setup(fetchPage: (q: MemberPageQuery) => Observable<Page<Member>>) {
  return TestBed.runInInjectionContext(() => new MemberListState(fetchPage, () => of(summary)));
}

describe('MemberListState · carga', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('only calls the wait "slow" after 1.2 s and clears it as soon as the answer arrives', () => {
    const answer = new Subject<Page<Member>>();
    const state = setup(() => answer);

    state.goTo(0);
    expect(state.loading()).toBe(true);
    expect(state.slow()).toBe(false);

    vi.advanceTimersByTime(MemberListState.SLOW_AFTER_MS - 1);
    expect(state.slow()).toBe(false);
    vi.advanceTimersByTime(2);
    expect(state.slow()).toBe(true);

    answer.next(page([1, 2]));
    expect(state.slow()).toBe(false);
    expect(state.loading()).toBe(false);
    expect(state.members().length).toBe(2);
  });

  it('a fast answer never shows the slow loader', () => {
    const state = setup(() => of(page([1])));
    state.goTo(0);
    vi.advanceTimersByTime(5000);
    expect(state.slow()).toBe(false);
  });

  it('repeating a filter within 30 s shows the saved answer at once and confirms it with the server underneath', () => {
    const calls: MemberPageQuery[] = [];
    const second = new Subject<Page<Member>>();
    let n = 0;
    const state = setup((q) => {
      calls.push(q);
      return ++n === 1 ? of(page([1])) : n === 2 ? of(page([9])) : second;
    });
    state.goTo(0); // 1.ª: sin filtro
    state.setStatusFilter('UNPAID'); // 2.ª: con filtro
    expect(state.members().map((m) => m.id)).toEqual([9]);

    state.setStatusFilter(null); // vuelve al listado sin filtro: ya estaba guardado
    expect(state.members().map((m) => m.id)).toEqual([1]); // al instante
    expect(state.stale()).toBe(true); // se muestra sin atenuar
    expect(state.loading()).toBe(true); // y se está confirmando

    second.next(page([1, 5])); // llega la confirmación con un dato nuevo
    expect(state.members().map((m) => m.id)).toEqual([1, 5]);
    expect(state.stale()).toBe(false);
    expect(state.loading()).toBe(false);
    expect(calls.length).toBe(3);
  });

  it('an answer older than 30 s is not reused: the list shows the wait instead of old data', () => {
    let n = 0;
    const second = new Subject<Page<Member>>();
    const state = setup(() => (++n === 1 ? of(page([1])) : second));
    state.goTo(0);
    vi.advanceTimersByTime(MemberListState.CACHE_TTL_MS + 1);

    state.goTo(0);

    expect(state.stale()).toBe(false);
  });

  it('reload (after a payment, a new member, a removal…) always asks for fresh data', () => {
    let n = 0;
    const second = new Subject<Page<Member>>();
    const state = setup(() => (++n === 1 ? of(page([1])) : second));
    state.reload();
    expect(state.loading()).toBe(false);

    state.reload();

    expect(state.stale()).toBe(false);
    expect(state.loading()).toBe(true);
  });

  it('a failed request ends the wait and flags the error', () => {
    const state = setup(() => new Observable<Page<Member>>((s) => s.error(new Error('x'))));
    state.goTo(0);
    expect(state.error()).toBe(true);
    expect(state.loading()).toBe(false);
    expect(state.slow()).toBe(false);
  });
});
