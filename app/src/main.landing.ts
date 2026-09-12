import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { webConfig } from './app/web.config';

bootstrapApplication(App, webConfig).catch((err) => console.error(err));
