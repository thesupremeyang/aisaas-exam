declare module 'vanta/dist/vanta.halo.min' {
  type VantaHaloOptions = {
    el: HTMLElement
    THREE: typeof import('three')
    mouseControls?: boolean
    touchControls?: boolean
    gyroControls?: boolean
    minHeight?: number
    minWidth?: number
    scale?: number
    scaleMobile?: number
    backgroundColor?: number
    baseColor?: number
    color2?: number
    amplitudeFactor?: number
    ringFactor?: number
    rotationFactor?: number
    xOffset?: number
    yOffset?: number
    size?: number
    speed?: number
  }

  type VantaEffect = {
    destroy(): void
    resize(): void
  }

  export default function HALO(options: VantaHaloOptions): VantaEffect
}
