import { Component, ElementRef, OnDestroy, afterNextRender, output, signal, viewChild } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeOutline } from 'ionicons/icons';
import jsQR from 'jsqr';

addIcons({ 'close-outline': closeOutline });

type ScannerState = 'starting' | 'scanning' | 'denied' | 'unsupported' | 'error';

// API nativa de Chrome en Android (no existe en iOS/Safari ni en Chrome de escritorio Windows/Linux).
interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
}
declare const BarcodeDetector: (new (options?: { formats: string[] }) => BarcodeDetectorLike) | undefined;

const SCAN_INTERVAL_MS = 120;
const MAX_DECODE_WIDTH = 640;

/**
 * Escáner de QR dentro de la app: abre la cámara trasera a pantalla completa, lee el primer QR que
 * vea y lo emite — sin salir de la app (el QR de la TV es un link normal que el teléfono abre con
 * el navegador; esto evita ese salto). Usa BarcodeDetector cuando el navegador lo trae y jsQR
 * como respaldo (iOS/Safari). Libera la cámara apenas termina o se destruye el componente.
 */
@Component({
  selector: 'app-qr-scanner',
  imports: [IonIcon],
  templateUrl: './qr-scanner.html',
  styleUrl: './qr-scanner.scss',
})
export class QrScanner implements OnDestroy {
  readonly scanned = output<string>();
  readonly closed = output<void>();

  protected readonly state = signal<ScannerState>('starting');

  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private stream: MediaStream | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private detector: BarcodeDetectorLike | null = null;
  private canvas: HTMLCanvasElement | null = null;
  private stopped = false;

  constructor() {
    afterNextRender(() => void this.start());
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  protected close(): void {
    this.stopCamera();
    this.closed.emit();
  }

  private async start(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) {
      this.state.set('unsupported');
      return;
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 } },
        audio: false,
      });
    } catch (err) {
      const name = (err as { name?: string })?.name;
      this.state.set(name === 'NotAllowedError' || name === 'SecurityError' ? 'denied' : 'error');
      return;
    }
    if (this.stopped) {
      this.stopCamera();
      return;
    }
    // Si el sistema corta la cámara (app en segundo plano, otra app la tomó) se avisa en vez de quedar congelado.
    this.stream.getVideoTracks().forEach((track) =>
      track.addEventListener('ended', () => {
        if (!this.stopped) {
          this.stopCamera();
          this.state.set('error');
        }
      }),
    );
    const video = this.video().nativeElement;
    video.srcObject = this.stream;
    try {
      await video.play();
    } catch {
      this.state.set('error');
      this.stopCamera();
      return;
    }
    if (typeof BarcodeDetector !== 'undefined') {
      try {
        this.detector = new BarcodeDetector({ formats: ['qr_code'] });
      } catch {
        this.detector = null;
      }
    }
    this.state.set('scanning');
    this.scheduleScan();
  }

  private scheduleScan(): void {
    if (this.stopped) {
      return;
    }
    this.timer = setTimeout(() => void this.scanOnce(), SCAN_INTERVAL_MS);
  }

  private async scanOnce(): Promise<void> {
    const video = this.video().nativeElement;
    let text: string | null = null;
    if (video.readyState >= 2 && video.videoWidth > 0) {
      try {
        text = this.detector ? await this.detectWithBarcodeDetector(video) : this.detectWithJsQr(video);
      } catch {
        text = null;
      }
    }
    if (this.stopped) {
      return;
    }
    if (text) {
      this.stopCamera();
      this.scanned.emit(text);
      return;
    }
    this.scheduleScan();
  }

  private async detectWithBarcodeDetector(video: HTMLVideoElement): Promise<string | null> {
    const results = await this.detector!.detect(video);
    return results.length > 0 ? results[0].rawValue : null;
  }

  private detectWithJsQr(video: HTMLVideoElement): string | null {
    const scale = Math.min(1, MAX_DECODE_WIDTH / video.videoWidth);
    const width = Math.round(video.videoWidth * scale);
    const height = Math.round(video.videoHeight * scale);
    const canvas = (this.canvas ??= document.createElement('canvas'));
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) {
      return null;
    }
    ctx.drawImage(video, 0, 0, width, height);
    // 'attemptBoth': el QR de la TV es "de marca" (colores del gimnasio) y puede salir claro sobre oscuro.
    const code = jsQR(ctx.getImageData(0, 0, width, height).data, width, height, { inversionAttempts: 'attemptBoth' });
    return code?.data ?? null;
  }

  private stopCamera(): void {
    this.stopped = true;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
  }
}
