import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideRouteSeo } from './route-seo';

@Component({ template: '' })
class Blank {}

describe('provideRouteSeo', () => {
  it('da canonical y descripción propios a las rutas con description y restaura los del index al salir', async () => {
    document.head.insertAdjacentHTML('beforeend', '<link rel="canonical" href="https://www.mygym.cl/"><meta name="description" content="home">');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: Blank },
          { path: 'faq', component: Blank, data: { description: 'faq desc' } },
        ]),
        provideRouteSeo(),
      ],
    });
    const router = TestBed.inject(Router);
    const canonical = () => document.querySelector<HTMLLinkElement>('link[rel="canonical"]')!.href;
    const description = () => document.querySelector<HTMLMetaElement>('meta[name="description"]')!.content;

    await router.navigateByUrl('/faq?x=1');
    expect(canonical()).toBe(location.origin + '/faq');
    expect(description()).toBe('faq desc');

    await router.navigateByUrl('/');
    expect(canonical()).toBe('https://www.mygym.cl/');
    expect(description()).toBe('home');
  });
});
