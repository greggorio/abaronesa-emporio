import { mockImage } from "./shared";

export const minimalistZen = {
  id: "clean-elegance",
  title: "Template 04 - Clean Elegance",
  subtitle:
    "Layout minimalista e elegante com tipografia sofisticada e separador refinado.",
  srcdoc: `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=1920, height=1080">
  <title>Clean Elegance Template</title>
  <style>
    :root {
      --token-vibrant: #c9a961;
      --token-muted: #8b7355;
      --token-light-vibrant: #e8d5a3;
      --token-dark-vibrant: #a08540;
      --token-light-muted: #f5f0e8;
      --token-dark-muted: #2c2416;
      --bg-main: #faf9f7;
      --bg-secondary: #f0ebe3;
      --bg-light: #ffffff;
      --bg-subtle: #f0ebe3;
      --text-main: #1a1a1a;
      --text-soft: #6b6b6b;
      --text-muted: #9b9b9b;
      --brand-primary: var(--token-vibrant);
      --brand-accent: var(--token-light-vibrant);
      --border-soft: #e0d5c7;
      --separator: #d4c4b0;
      --separator-rgb: 212, 196, 176;
      --safe-area: 100px;
    }
    :root.is-dark {
      --bg-main: #1a1a1a;
      --bg-secondary: #2a2a2a;
      --bg-light: #2a2a2a;
      --bg-subtle: #2a2a2a;
      --text-main: #f5f0e8;
      --text-soft: #b0a090;
      --text-muted: #8b7d6b;
      --border-soft: #3a3a3a;
      --separator: #4a4a4a;
      --separator-rgb: 74, 74, 74;
    }
    *, *::before, *::after {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }
    html, body {
      width: 1920px;
      height: 1080px;
      overflow: hidden;
      font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    }
    .template-container {
      position: relative;
      width: 1920px;
      height: 1080px;
      background: var(--bg-main);
      padding: 80px;
    }
    /* Particles Rain Effect */
    .particles {
      position: absolute;
      inset: 0;
      overflow: hidden;
      pointer-events: none;
      z-index: 0;
    }
    .particle {
      position: absolute;
      width: 3px;
      height: 3px;
      background: var(--token-vibrant);
      border-radius: 50%;
      opacity: 0.25;
      animation:
        rain var(--duration, 8s) linear infinite,
        particleGlow 3s ease-in-out infinite;
    }
    .particle:nth-child(1) { left: 10%; animation-delay: 0s; --duration: 10s; }
    .particle:nth-child(2) { left: 20%; animation-delay: 1s; --duration: 14s; }
    .particle:nth-child(3) { left: 30%; animation-delay: 2s; --duration: 11s; }
    .particle:nth-child(4) { left: 40%; animation-delay: 3s; --duration: 13s; }
    .particle:nth-child(5) { left: 50%; animation-delay: 0.5s; --duration: 9s; }
    .particle:nth-child(6) { left: 60%; animation-delay: 1.5s; --duration: 12s; }
    .particle:nth-child(7) { left: 70%; animation-delay: 2.5s; --duration: 15s; }
    .particle:nth-child(8) { left: 80%; animation-delay: 3.5s; --duration: 8s; }
    .particle:nth-child(9) { left: 90%; animation-delay: 4s; --duration: 11.5s; }

    /* Sequential Entrance Animations */
    .badge {
      font-size: 14px;
      font-weight: 500;
      letter-spacing: 4px;
      text-transform: uppercase;
      background: var(--bg-light);
      color: var(--text-main);
      padding: 10px 20px;
      margin-bottom: 20px;
      animation:
        badgeBounce 0.8s cubic-bezier(0.68, -0.55, 0.265, 1.55) 0.3s both,
        badgeShine 3s ease-in-out 2s infinite;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04),
                  inset 0 0 0 1px rgba(0, 0, 0, 0.04);
    }
    .badge:empty {
      display: none;
    }
    .headline {
      font-family: "Georgia", "Times New Roman", serif;
      font-size: 80px;
      font-weight: 600;
      line-height: 1.1;
      letter-spacing: 6px;
      color: var(--token-dark-muted);
      text-transform: uppercase;
      margin-bottom: 20px;
      position: relative;
      animation: fadeSlideUp 1s cubic-bezier(0.4, 0, 0.2, 1) 0.3s both;
    }
    /* Shimmer Effect on Headline */
    .headline::after {
      content: '';
      position: absolute;
      top: 0;
      left: -100%;
      width: 100%;
      height: 100%;
      background: linear-gradient(90deg, 
        transparent 0%, 
        rgba(255,255,255,0) 20%, 
        rgba(255,255,255,0.3) 50%, 
        rgba(255,255,255,0) 80%, 
        transparent 100%);
      animation: shimmer 4s ease-in-out 2s infinite;
      pointer-events: none;
    }
    .subtitle {
      font-size: 16px;
      font-weight: 400;
      line-height: 1.6;
      letter-spacing: 1.5px;
      color: var(--token-muted);
      text-transform: uppercase;
      max-width: 600px;
      margin: 0 auto 30px;
      display: block;
      width: min(600px, fit-content);
      animation: fadeIn 0.8s ease-out 0.6s both;
    }
    .subtitle-text {
      display: inline-block;
      overflow: hidden;
      white-space: nowrap;
      border-right: 2px solid var(--separator);
      width: 0;
      --typing-width: 0px;
      animation: none;
    }
    .price-section {
      display: flex;
      align-items: flex-start;
      justify-content: center;
      gap: 4px;
      margin-bottom: 30px;
      min-height: 120px;
      transform-origin: center;
      animation:
        priceImpact 0.6s cubic-bezier(0.68, -0.55, 0.265, 1.55) 1.5s both,
        pricePulse 2s ease-in-out 2.5s infinite;
    }
    .price-currency {
      font-family: "Georgia", "Times New Roman", serif;
      font-size: 32px;
      font-weight: 400;
      color: var(--text-main);
      margin-top: 8px;
    }
    .price-main {
      font-family: "Georgia", "Times New Roman", serif;
      font-size: 96px;
      font-weight: 500;
      color: var(--text-main);
      letter-spacing: -2px;
      line-height: 1;
    }
    .price-cents {
      font-family: "Georgia", "Times New Roman", serif;
      font-size: 48px;
      font-weight: 500;
      color: var(--text-main);
      letter-spacing: -1px;
      margin-top: 4px;
    }
    .content-wrapper {
      position: absolute;
      top: 80px;
      left: 50%;
      transform: translateX(-50%);
      text-align: center;
      width: 100%;
      max-width: 1400px;
      z-index: 2;
    }
    .separator {
      width: 2px;
      height: 50px;
      background: var(--separator);
      margin: 0 auto 20px;
      animation:
        growHeight 0.6s ease-out 1.2s both,
        pulse 3s ease-in-out 2s infinite,
        energyPulse 2s ease-in-out 2s infinite;
      box-shadow: 0 0 0 rgba(var(--separator-rgb), 0);
    }
    .image-wrapper {
      position: absolute;
      bottom: 60px;
      left: 50%;
      transform: translateX(-50%);
      width: 600px;
      height: 450px;
      z-index: 1;
      animation: fadeScale 1s cubic-bezier(0.4, 0, 0.2, 1) 1.5s both;
    }
    .image-wrapper::after {
      content: "";
      position: absolute;
      inset: -60px;
      background: radial-gradient(circle, var(--token-vibrant) 0%, transparent 60%);
      filter: blur(80px);
      opacity: 0;
      z-index: 0;
      animation: glowAppetite 3s ease-in-out 2s infinite;
    }
    .image-section {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: flex-end;
      justify-content: center;
      animation:
        kenBurns 8s ease-out both,
        float 6s ease-in-out 2s infinite,
        breathe 4s ease-in-out 3s infinite;
      filter:
        drop-shadow(0 20px 40px rgba(0, 0, 0, 0.15))
        brightness(1)
        saturate(1);
    }
    .product-image {
      max-width: 100%;
      max-height: 100%;
      width: auto;
      height: auto;
      object-fit: contain;
      filter: drop-shadow(0 20px 40px rgba(0, 0, 0, 0.15));
      background: transparent;
      transition: opacity 0.3s ease;
    }

    .product-image:not([src]),
    .product-image[src=""] {
      opacity: 0;
    }

    /* Keyframes */
    @keyframes fadeSlideDown {
      from { opacity: 0; transform: translateY(-30px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @keyframes fadeSlideUp {
      from { opacity: 0; transform: translateY(40px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    @keyframes growHeight {
      from { opacity: 0; transform: scaleY(0); }
      to { opacity: 1; transform: scaleY(1); }
    }
    @keyframes fadeScale {
      from { opacity: 0; transform: translateX(-50%) scale(0.9); }
      to { opacity: 1; transform: translateX(-50%) scale(1); }
    }
    @keyframes shimmer {
      0% { left: -100%; }
      50%, 100% { left: 100%; }
    }
    @keyframes typing {
      from { width: 0; }
      to { width: var(--typing-width, 0px); }
    }
    @keyframes blink {
      0%, 50%, 100% { border-color: transparent; }
      25%, 75% { border-color: var(--separator); }
    }
    @keyframes pulse {
      0%, 100% {
        opacity: 1;
        transform: scaleY(1);
        box-shadow: 0 0 0 rgba(var(--separator-rgb), 0);
      }
      50% {
        opacity: 0.7;
        transform: scaleY(1.15);
        box-shadow: 0 0 20px rgba(var(--separator-rgb), 0.3);
      }
    }
    @keyframes float {
      0%, 100% { transform: translateY(0); }
      50% { transform: translateY(-15px); }
    }
    @keyframes kenBurns {
      from {
        transform: scale(1.08);
        filter: brightness(1.02);
      }
      to {
        transform: scale(1);
        filter: brightness(1);
      }
    }
    @keyframes rain {
      from { transform: translateY(-10px); opacity: 0; }
      10% { opacity: 0.15; }
      90% { opacity: 0.15; }
      to { transform: translateY(110vh); opacity: 0; }
    }
    @keyframes badgeBounce {
      0% {
        opacity: 0;
        transform: translateY(-50px) rotate(-10deg);
      }
      60% {
        transform: translateY(5px) rotate(2deg);
      }
      100% {
        opacity: 1;
        transform: translateY(0) rotate(0);
      }
    }
    @keyframes badgeShine {
      0%, 100% {
        filter: brightness(1);
      }
      50% {
        filter: brightness(1.15);
      }
    }
    @keyframes priceImpact {
      0% {
        opacity: 0;
        transform: scale(0.5) translateY(30px);
      }
      60% {
        transform: scale(1.1) translateY(0);
      }
      100% {
        opacity: 1;
        transform: scale(1);
      }
    }
    @keyframes pricePulse {
      0%, 100% {
        transform: scale(1);
        filter: brightness(1);
      }
      50% {
        transform: scale(1.03);
        filter: brightness(1.15);
      }
    }
    @keyframes breathe {
      0%, 100% {
        filter:
          drop-shadow(0 20px 40px rgba(0, 0, 0, 0.15))
          brightness(1)
          saturate(1);
      }
      50% {
        filter:
          drop-shadow(0 25px 50px rgba(0, 0, 0, 0.2))
          brightness(1.08)
          saturate(1.15);
      }
    }
    @keyframes glowAppetite {
      0%, 100% { opacity: 0; }
      50% { opacity: 0.3; }
    }
    @keyframes particleGlow {
      0%, 100% { opacity: 0.15; }
      50% { opacity: 0.35; }
    }
    @keyframes energyPulse {
      0%, 100% {
        opacity: 1;
        transform: scaleY(1);
        box-shadow: 0 0 0 rgba(var(--separator-rgb), 0);
      }
      50% {
        opacity: 0.8;
        transform: scaleY(1.2);
        box-shadow: 0 0 20px rgba(201, 169, 97, 0.6);
      }
    }
    .corner-accent {
      position: absolute;
      width: 40px;
      height: 40px;
      border: 1px solid var(--border-soft);
      z-index: 3;
    }
    .corner-accent.top-left {
      top: 40px;
      left: 40px;
      border-right: none;
      border-bottom: none;
    }
    .corner-accent.top-right {
      top: 40px;
      right: 40px;
      border-left: none;
      border-bottom: none;
    }
    .corner-accent.bottom-left {
      bottom: 40px;
      left: 40px;
      border-right: none;
      border-top: none;
    }
    .corner-accent.bottom-right {
      bottom: 40px;
      right: 40px;
      border-left: none;
      border-top: none;
    }
  </style>
</head>
<body>
  <div class="template-container">
    <div class="particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <div class="corner-accent top-left"></div>
    <div class="corner-accent top-right"></div>
    <div class="corner-accent bottom-left"></div>
    <div class="corner-accent bottom-right"></div>

    <div class="content-wrapper">
      <div class="badge" id="badge">Clássico</div>
      <h1 class="headline" id="headline">Aussie Lamb Burger</h1>
      <p class="subtitle" id="subtitle"><span class="subtitle-text" id="subtitleText">Tasty lamb patty, lettuce, cheese, mustard, pickles</span></p>

      <div class="price-section">
        <span class="price-currency">R$</span>
        <span class="price-main" id="priceMain">18</span>
        <span class="price-cents" id="priceCents">,90</span>
      </div>

      <div class="separator"></div>
    </div>

    <div class="image-wrapper">
      <div class="image-section">
        <img class="product-image" id="productImage" src="" alt="Product" />
      </div>
    </div>
  </div>

   <script>
    (function() {
      const mockData = { image: "${mockImage}" };

      // Função auxiliar para ajustar brilho de uma cor HEX
      function adjustColor(hex, percent) {
        const sanitized = (hex || '').trim();
        if (!/^#([0-9a-fA-F]{6})$/.test(sanitized)) return hex;
        const num = parseInt(sanitized.replace('#', ''), 16);
        const amt = Math.round(2.55 * percent);
        const R = Math.max(0, Math.min(255, (num >> 16) + amt));
        const G = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + amt));
        const B = Math.max(0, Math.min(255, (num & 0x0000FF) + amt));
        return '#' + (0x1000000 + R * 0x10000 + G * 0x100 + B).toString(16).slice(1);
      }

      const colorConfig = {
        background: {
          resolvedKeys: ['background'],
          paletteKeys: ['background'],
          cacheKey: 'background',
          safe: '#000000',
        },
        text: {
          resolvedKeys: ['text', 'headline'],
          paletteKeys: ['text'],
          cacheKey: 'text',
          safe: '#ffffff',
          cssVars: '--text-main',
        },
        subtitle: {
          resolvedKeys: ['subtitle'],
          paletteKeys: ['accent2', 'subtitle'],
          cacheKey: 'subtitle',
          safe: '#ffffff',
          cssVars: '--text-soft',
        },
        badge: {
          resolvedKeys: ['badge'],
          paletteKeys: ['accent', 'badge'],
          cacheKey: 'badge',
          safe: '#ffffff',
          cssVars: ['--brand-primary'],
        },
        separator: {
          resolvedKeys: ['separator'],
          paletteKeys: ['separator', 'muted'],
          cacheKey: 'separator',
          safe: '#000000',
          cssVars: '--separator',
        },
        price: {
          resolvedKeys: ['price'],
          paletteKeys: ['price', 'vibrant'],
          cacheKey: 'price',
          safe: '#ffffff',
        },
      };

      const captureTemplateDefaults = (() => {
        let cache = null;
        return (root, priceElement) => {
          if (cache) return cache;
          const computed = window.getComputedStyle(root);
          const priceComputed = priceElement ? window.getComputedStyle(priceElement) : null;
          cache = {
            background: computed.getPropertyValue('--bg-main').trim(),
            backgroundSecondary: computed.getPropertyValue('--bg-secondary').trim(),
            backgroundLight: computed.getPropertyValue('--bg-light').trim(),
            backgroundSubtle: computed.getPropertyValue('--bg-subtle').trim(),
            text: computed.getPropertyValue('--text-main').trim(),
            subtitle: computed.getPropertyValue('--text-soft').trim(),
            badge: computed.getPropertyValue('--brand-primary').trim(),
            separator: computed.getPropertyValue('--separator').trim(),
            separatorRgb: computed.getPropertyValue('--separator-rgb').trim(),
            price: priceComputed ? priceComputed.color.trim() : '',
          };
          return cache;
        };
      })();

      const pickColorValue = (config, palette, resolvedColors, defaults) => {
        const resolvedKeys = Array.isArray(config.resolvedKeys)
          ? config.resolvedKeys
          : [config.resolvedKeys];
        for (const key of resolvedKeys) {
          const value = resolvedColors[key];
          if (value && value.trim()) {
            return { color: value, stage: 'resolved' };
          }
        }

        const paletteKeys = Array.isArray(config.paletteKeys)
          ? config.paletteKeys
          : [config.paletteKeys];
        for (const key of paletteKeys) {
          const paletteValue = palette[key];
          if (typeof paletteValue === 'string' && paletteValue.trim()) {
            return { color: paletteValue, stage: 'palette' };
          }
        }

        if (config.cacheKey && defaults && defaults[config.cacheKey]) {
          return { color: defaults[config.cacheKey], stage: 'default' };
        }

        return { color: config.safe, stage: 'safe' };
      };

      const applyCssVars = (root, vars, color) => {
        if (!vars) return;
        const names = Array.isArray(vars) ? vars : [vars];
        names.forEach((name) => {
          if (color) {
            root.style.setProperty(name, color);
          } else {
            root.style.removeProperty(name);
          }
        });
      };

      const applyElementColor = (element, color) => {
        if (!element) return;
        if (color) {
          element.style.color = color;
        } else {
          element.style.removeProperty('color');
        }
      };

      const toRgb = (hex) => {
        const sanitized = (hex || '').trim();
        if (!/^#([0-9a-fA-F]{6})$/.test(sanitized)) return '0, 0, 0';
        const r = parseInt(sanitized.slice(1, 3), 16);
        const g = parseInt(sanitized.slice(3, 5), 16);
        const b = parseInt(sanitized.slice(5, 7), 16);
        return r + ', ' + g + ', ' + b;
      };

      const applySeparatorColor = (root, colorChoice, defaults) => {
        const color = colorChoice.color;
        applyCssVars(root, colorConfig.separator.cssVars, color);
        const rgb = colorChoice.stage === 'default' && defaults.separatorRgb
          ? defaults.separatorRgb
          : toRgb(color);
        root.style.setProperty('--separator-rgb', rgb);
      };

      const applyBackgroundColors = (root, colorChoice, defaults) => {
        const base = colorChoice.color;
        if (!base) return;
        if (colorChoice.stage === 'default') {
          root.style.setProperty('--bg-main', defaults.background || base);
          root.style.setProperty('--bg-secondary', defaults.backgroundSecondary || base);
          root.style.setProperty('--bg-light', defaults.backgroundLight || base);
          root.style.setProperty('--bg-subtle', defaults.backgroundSubtle || base);
          return;
        }
        root.style.setProperty('--bg-main', base);
        root.style.setProperty('--bg-secondary', adjustColor(base, -10));
        root.style.setProperty('--bg-light', base);
        root.style.setProperty('--bg-subtle', adjustColor(base, -5));
      };

      const applyImageWithFallback = (imageElement, source) => {
        if (!imageElement) return;
        const fallback = mockData.image;
        const hideImage = () => {
          imageElement.style.opacity = '0';
          imageElement.removeAttribute('src');
          imageElement.dataset.fallbackStage = 'hidden';
        };
        const setSource = (url, stage) => {
          imageElement.dataset.fallbackStage = stage;
          if (url) {
            imageElement.style.opacity = '1';
            imageElement.src = url;
          } else {
            hideImage();
          }
        };
        imageElement.onerror = () => {
          if (imageElement.dataset.fallbackStage === 'primary' && fallback) {
            setSource(fallback, 'mock');
            return;
          }
          hideImage();
        };
        if (source) {
          setSource(source, 'primary');
        } else if (fallback) {
          setSource(fallback, 'mock');
        } else {
          hideImage();
        }
      };

      function applyData() {
        const data = Object.assign({}, mockData, window.__SIGNAGE_PREVIEW__ || {});
        const palette = data.palette || {};
        const resolvedColors = data.resolvedColors || {};
        const root = document.documentElement;

        // Captura dos elementos que serão estilizados
        const badge = document.getElementById('badge');
        const headline = document.getElementById('headline');
        const subtitleText = document.getElementById('subtitleText');
        const priceMain = document.getElementById('priceMain');
        const priceCents = document.getElementById('priceCents');
        const productImage = document.getElementById('productImage');

        // Aplicar cores da paleta vibrantes
        if (palette.vibrant) root.style.setProperty('--token-vibrant', palette.vibrant);
        if (palette.muted) root.style.setProperty('--token-muted', palette.muted);
        if (palette.lightVibrant) root.style.setProperty('--token-light-vibrant', palette.lightVibrant);
        if (palette.darkVibrant) root.style.setProperty('--token-dark-vibrant', palette.darkVibrant);
        if (palette.lightMuted) root.style.setProperty('--token-light-muted', palette.lightMuted);
        if (palette.darkMuted) root.style.setProperty('--token-dark-muted', palette.darkMuted);

        const defaults = captureTemplateDefaults(root, priceMain);

        const backgroundChoice = pickColorValue(colorConfig.background, palette, resolvedColors, defaults);
        applyBackgroundColors(root, backgroundChoice, defaults);

        const textChoice = pickColorValue(colorConfig.text, palette, resolvedColors, defaults);
        applyCssVars(root, colorConfig.text.cssVars, textChoice.color);

        const subtitleChoice = pickColorValue(colorConfig.subtitle, palette, resolvedColors, defaults);
        applyCssVars(root, colorConfig.subtitle.cssVars, subtitleChoice.color);

        const badgeChoice = pickColorValue(colorConfig.badge, palette, resolvedColors, defaults);
        applyCssVars(root, colorConfig.badge.cssVars, badgeChoice.color);

        const separatorChoice = pickColorValue(colorConfig.separator, palette, resolvedColors, defaults);
        applySeparatorColor(root, separatorChoice, defaults);

        const priceChoice = pickColorValue(colorConfig.price, palette, resolvedColors, defaults);
        applyElementColor(priceMain, priceChoice.color);
        applyElementColor(priceCents, priceChoice.color);

        // Fallback para comportamento antigo (isDark) se não houver resolvedColors
        const hasResolved = Object.keys(resolvedColors).length > 0;
        root.classList.remove('is-dark');
        if (!hasResolved && palette.isDark === true) {
          root.classList.add('is-dark');
        }

        badge.textContent = data.badge || '';
        headline.textContent = data.headline || '';
        if (subtitleText) {
          subtitleText.textContent = data.subtitle || '';
          subtitleText.style.animation = 'none';
          subtitleText.style.width = 'auto';
          subtitleText.style.setProperty('--typing-width', subtitleText.scrollWidth + 'px');
          subtitleText.style.width = '0px';
          void subtitleText.offsetWidth;
          const steps = Math.max(10, subtitleText.textContent.length);
          subtitleText.style.animation =
            'typing 6s steps(' + steps + ') 0.8s forwards, ' +
            'blink 0.75s step-end 6.8s 6 forwards';
        }

        const priceStr = data.price || '';
        const priceParts = priceStr.replace(',', '.').split('.');
        priceMain.textContent = priceParts[0] || '';
        priceCents.textContent = priceParts[1] ? ',' + priceParts[1] : '';

        applyImageWithFallback(productImage, data.image);
      }

      if (window.__SIGNAGE_PREVIEW__) {
        applyData();
      } else {
        window.addEventListener('__SIGNAGE_PREVIEW_READY__', applyData);
      }
    })();
  </script>
</body>
</html>`,
};
