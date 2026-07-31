import { mockImage } from "./shared";

export const splitView = {
  id: "editorial-overlay-enhanced",
  title: "Template 02 - Editorial Overlay Enhanced",
  subtitle:
    "Imagem full-bleed com tipografia sobreposta e animações aprimoradas.",
  srcdoc: `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <title>Editorial Overlay Template</title>
    <style>
      *, *::before, *::after {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
      }
      :root {
        --token-vibrant: #e0b15a;
        --token-muted: #7a6a52;
        --token-light-vibrant: #f2d19b;
        --token-dark-vibrant: #a0782b;
        --token-light-muted: #e7ddcf;
        --token-dark-muted: #2b241c;
        --bg-main: color-mix(in srgb, var(--token-dark-muted) 85%, black 15%);
        --text-main: var(--token-light-muted);
        --text-soft: color-mix(in srgb, var(--token-light-muted) 65%, var(--token-muted) 35%);
        --accent: var(--token-vibrant);
        --accent-soft: var(--token-light-vibrant);
        --accent-glow: color-mix(in srgb, var(--token-vibrant) 40%, transparent 60%);
        --line: color-mix(in srgb, var(--token-light-muted) 35%, transparent 65%);
        --overlay-strong: color-mix(in srgb, var(--token-dark-muted) 92%, black 8%);
        --overlay-soft: color-mix(in srgb, var(--token-dark-muted) 35%, transparent 65%);
        --vignette: rgba(0, 0, 0, 0.6);
        --safe-area: 90px;
      }
      :root.is-light {
        --bg-main: color-mix(in srgb, var(--token-light-muted) 92%, white 8%);
        --text-main: var(--token-dark-muted);
        --text-soft: color-mix(in srgb, var(--token-dark-muted) 60%, var(--token-muted) 40%);
        --accent: var(--token-dark-vibrant);
        --accent-soft: var(--token-vibrant);
        --line: color-mix(in srgb, var(--token-dark-muted) 25%, transparent 75%);
        --overlay-strong: color-mix(in srgb, var(--token-light-muted) 85%, white 15%);
        --overlay-soft: color-mix(in srgb, var(--token-light-muted) 25%, transparent 75%);
        --vignette: rgba(0, 0, 0, 0.25);
      }
      html, body {
        width: 1920px;
        height: 1080px;
        overflow: hidden;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
        background: var(--bg-main);
        color: var(--text-main);
      }
      body {
        position: relative;
      }
      .frame {
        position: relative;
        width: 100%;
        height: 100%;
        overflow: hidden;
      }
      .bg-image {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        object-fit: contain;
        object-position: center;
        filter: saturate(1.08) contrast(1.04) brightness(0.98);
        transform: scale(1);
        animation: kenBurnsEnhanced 8s cubic-bezier(0.45, 0.05, 0.55, 0.95) infinite;
      }
      .overlay {
        position: absolute;
        inset: 0;
        background: linear-gradient(105deg, 
          var(--overlay-strong) 0%, 
          color-mix(in srgb, var(--overlay-strong) 65%, transparent 35%) 42%,
          color-mix(in srgb, var(--overlay-soft) 80%, transparent 20%) 62%,
          transparent 88%
        );
        z-index: 1;
      }
      .vignette {
        position: absolute;
        inset: 0;
        background: radial-gradient(ellipse 75% 65% at 50% 50%, transparent 35%, var(--vignette) 100%);
        z-index: 1;
      }
      .accent-glow {
        position: absolute;
        left: -5%;
        top: 50%;
        transform: translateY(-50%);
        width: 500px;
        height: 500px;
        background: radial-gradient(circle, var(--accent-glow) 0%, transparent 70%);
        filter: blur(80px);
        z-index: 0;
        animation: glowPulse 3s ease-in-out infinite;
      }
      .corner-line {
        position: absolute;
        border: 2px solid var(--line);
        z-index: 2;
        animation: framePulse 4s ease-in-out infinite;
      }
      .corner-line.top-left {
        top: 50px;
        left: 50px;
        width: 220px;
        height: 220px;
        border-right: none;
        border-bottom: none;
        border-radius: 8px 0 0 0;
      }
      .corner-line.bottom-right {
        bottom: 50px;
        right: 50px;
        width: 180px;
        height: 180px;
        border-left: none;
        border-top: none;
        border-radius: 0 0 8px 0;
      }
      .floating-square {
        position: absolute;
        top: 55%;
        right: 8%;
        width: 110px;
        height: 110px;
        border: 1.5px solid var(--line);
        opacity: 0;
        transform: rotate(12deg);
        animation: shapeFloat 6s ease-in-out 1.2s infinite;
        z-index: 2;
      }
      .floating-circle {
        position: absolute;
        top: 15%;
        right: 12%;
        width: 90px;
        height: 90px;
        border: 1.5px solid var(--line);
        border-radius: 50%;
        opacity: 0;
        animation: shapeFloat 6s ease-in-out 0.5s infinite;
        z-index: 2;
      }
      .particle {
        position: absolute;
        width: 10px;
        height: 10px;
        background: var(--accent);
        border-radius: 50%;
        opacity: 0;
        box-shadow: 0 0 16px var(--accent-glow);
        animation: particleDrift 5s ease-in-out infinite;
        z-index: 2;
      }
      .particle.one { top: 25%; right: 18%; animation-delay: 0.3s; }
      .particle.two { top: 48%; right: 25%; animation-delay: 1.1s; }
      .particle.three { top: 68%; right: 14%; animation-delay: 2s; }
      .particle.four { top: 35%; right: 32%; animation-delay: 2.8s; }

      .content {
        position: relative;
        z-index: 3;
        display: flex;
        flex-direction: column;
        justify-content: center;
        height: 100%;
        padding: 0 0 0 110px;
        max-width: 950px;
      }
      .badge-row {
        display: flex;
        align-items: center;
        gap: 20px;
        text-transform: uppercase;
        letter-spacing: 5px;
        font-size: 16px;
        font-weight: 700;
        color: var(--accent);
        margin-bottom: 28px;
        opacity: 0;
        animation: slideInFade 0.7s cubic-bezier(0.34, 1.56, 0.64, 1) 0.3s forwards;
        background: color-mix(in srgb, var(--token-dark-muted) 85%, black 15%);
        padding: 14px 28px 14px 0;
        border-radius: 50px;
        width: fit-content;
        backdrop-filter: blur(10px);
        text-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
      }
      .badge-line {
        width: 65px;
        height: 3px;
        background: var(--accent);
        transform: scaleX(0);
        transform-origin: left;
        animation: lineExpand 0.6s ease-out 0.5s forwards;
        margin-left: 24px;
        box-shadow: 0 0 12px var(--accent), 0 0 20px var(--accent-glow);
        border-radius: 2px;
      }
      .headline {
        font-size: 128px;
        font-weight: 600;
        letter-spacing: -4px;
        line-height: 0.95;
        text-shadow: 
          0 12px 40px rgba(0, 0, 0, 0.6),
          0 4px 12px rgba(0, 0, 0, 0.4);
        margin-bottom: 32px;
        opacity: 0;
        animation: titleReveal 0.9s cubic-bezier(0.22, 1, 0.36, 1) 0.6s forwards;
      }
      .subtitle {
        font-size: 32px;
        font-weight: 400;
        line-height: 1.4;
        color: var(--text-soft);
        max-width: 680px;
        margin-bottom: 42px;
        opacity: 0;
        animation: slideInFade 0.8s ease-out 1.1s forwards;
        text-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);
      }
      .price-row {
        display: flex;
        align-items: flex-end;
        gap: 8px;
        margin-bottom: 48px;
        opacity: 0;
        animation: priceReveal 0.7s cubic-bezier(0.34, 1.56, 0.64, 1) 1.5s forwards;
      }
      .price-currency {
        font-size: 36px;
        font-weight: 700;
        color: var(--accent);
        margin-bottom: 8px;
        letter-spacing: -1px;
      }
      .price-main {
        font-size: 88px;
        font-weight: 900;
        color: var(--text-main);
        letter-spacing: -3px;
        line-height: 1;
        text-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
      }
      .price-cents {
        font-size: 42px;
        font-weight: 700;
        color: var(--text-main);
        margin-bottom: 10px;
      }
      .cta {
        font-size: 24px;
        color: var(--token-dark-muted);
        background: linear-gradient(135deg, var(--accent) 0%, var(--accent-soft) 100%);
        display: inline-flex;
        align-items: center;
        gap: 18px;
        text-decoration: none;
        font-weight: 700;
        width: fit-content;
        padding: 22px 48px;
        border-radius: 12px;
        box-shadow: 
          0 12px 32px color-mix(in srgb, var(--accent) 45%, transparent 55%),
          inset 0 1px 0 rgba(255, 255, 255, 0.4);
        opacity: 0;
        animation: ctaReveal 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) 1.9s forwards;
      }
      .cta svg {
        width: 24px;
        height: 24px;
      }

      @keyframes kenBurnsEnhanced {
        0% { 
          transform: scale(1) translate(0, 0);
          filter: saturate(1.08) contrast(1.04) brightness(0.98);
        }
        50% { 
          transform: scale(1.03) translate(-8px, -4px);
          filter: saturate(1.12) contrast(1.06) brightness(1.0);
        }
        100% { 
          transform: scale(1) translate(0, 0);
          filter: saturate(1.08) contrast(1.04) brightness(0.98);
        }
      }
      @keyframes slideInFade {
        from {
          opacity: 0;
          transform: translateX(-50px);
        }
        to {
          opacity: 1;
          transform: translateX(0);
        }
      }
      @keyframes titleReveal {
        from {
          opacity: 0;
          transform: translateY(40px) scale(0.95);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }
      @keyframes priceReveal {
        from {
          opacity: 0;
          transform: translateY(30px) scale(0.9);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }
      @keyframes ctaReveal {
        from {
          opacity: 0;
          transform: translateY(20px) scale(0.95);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }
      @keyframes lineExpand {
        from {
          transform: scaleX(0);
          opacity: 0;
        }
        to {
          transform: scaleX(1);
          opacity: 1;
        }
      }
      @keyframes framePulse {
        0%, 100% { 
          opacity: 0.45;
          transform: scale(1);
        }
        50% { 
          opacity: 0.8;
          transform: scale(1.02);
        }
      }
      @keyframes shapeFloat {
        0%, 100% { 
          opacity: 0;
          transform: translateY(0) rotate(0deg);
        }
        15%, 85% {
          opacity: 0.65;
        }
        50% { 
          opacity: 0.85;
          transform: translateY(-25px) rotate(8deg);
        }
      }
      @keyframes particleDrift {
        0%, 100% { 
          opacity: 0;
          transform: translateY(0) scale(1);
        }
        20%, 80% {
          opacity: 0.9;
        }
        50% { 
          opacity: 1;
          transform: translateY(-35px) scale(1.5);
        }
      }
      @keyframes glowPulse {
        0%, 100% { 
          opacity: 0.3;
          transform: translateY(-50%) scale(1);
        }
        50% { 
          opacity: 0.6;
          transform: translateY(-50%) scale(1.15);
        }
      }
    </style>
  </head>
  <body>
    <div class="frame">
      <img class="bg-image" id="productImage" src="${mockImage}" alt="Product" />
      <div class="overlay"></div>
      <div class="vignette"></div>
      <div class="accent-glow"></div>
      <div class="corner-line top-left"></div>
      <div class="corner-line bottom-right"></div>
      <div class="floating-square"></div>
      <div class="floating-circle"></div>
      <div class="particle one"></div>
      <div class="particle two"></div>
      <div class="particle three"></div>
      <div class="particle four"></div>

      <div class="content">
        <div class="badge-row">
          <span class="badge-line"></span>
          <span id="badge">Chef's Special</span>
        </div>
        <h1 class="headline" id="headline">Filé Mignon</h1>
        <p class="subtitle" id="subtitle">ao molho de vinho tinto com ervas finas</p>
        <div class="price-row">
          <span class="price-currency">R$</span>
          <span class="price-main" id="priceMain">89</span>
          <span class="price-cents" id="priceCents">,00</span>
        </div>
        <a class="cta" href="#">
          <span id="ctaText">Peça agora</span>
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
          </svg>
        </a>
      </div>
    </div>

    <script>
      (function() {
        const mockData = {
          badge: "Chef's Special",
          headline: "Filé Mignon",
          subtitle: "ao molho de vinho tinto com ervas finas",
          cta: "Peça agora",
          price: "89,00",
          image: "${mockImage}",
          palette: {
            vibrant: "#e0b15a",
            muted: "#7a6a52",
            lightVibrant: "#f2d19b",
            darkVibrant: "#a0782b",
            lightMuted: "#e7ddcf",
            darkMuted: "#2b241c",
            isDark: false
          }
        };

        const colorConfig = {
          background: {
            resolvedKeys: ['background'],
            paletteKeys: ['background'],
            cacheKey: 'background',
            safe: '#000000',
            cssVars: ['--bg-main', '--bg-secondary'],
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
            paletteKeys: ['accent2'],
            cacheKey: 'subtitle',
            safe: '#ffffff',
            cssVars: '--text-soft',
          },
          badge: {
            resolvedKeys: ['badge'],
            paletteKeys: ['accent'],
            cacheKey: 'badge',
            safe: '#ffffff',
            cssVars: ['--brand-primary', '--accent', '--accent-soft'],
          },
          separator: {
            resolvedKeys: ['separator'],
            paletteKeys: ['muted'],
            cacheKey: 'separator',
            safe: '#000000',
            cssVars: ['--separator', '--line'],
          },
          price: {
            resolvedKeys: ['price'],
            paletteKeys: ['vibrant'],
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
              text: computed.getPropertyValue('--text-main').trim(),
              subtitle: computed.getPropertyValue('--text-soft').trim(),
              badge: computed.getPropertyValue('--brand-primary').trim(),
              accent: computed.getPropertyValue('--accent').trim(),
              accentSoft: computed.getPropertyValue('--accent-soft').trim(),
              separator: computed.getPropertyValue('--separator').trim(),
              line: computed.getPropertyValue('--line').trim(),
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
              return value;
            }
          }

          const paletteKeys = Array.isArray(config.paletteKeys)
            ? config.paletteKeys
            : [config.paletteKeys];
          for (const key of paletteKeys) {
            const paletteValue = palette[key];
            if (typeof paletteValue === 'string' && paletteValue.trim()) {
              return paletteValue;
            }
          }

          if (config.cacheKey && defaults && defaults[config.cacheKey]) {
            return defaults[config.cacheKey];
          }

          return config.safe;
        };

        const applyCssVars = (root, vars, color) => {
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

        function normalizePrice(value) {
          if (value == null) return '';
          const str = String(value).trim();
          if (!str) return '';
          return str.replace(/^R\$\s*/i, '').replace(/^\$\s*/, '');
        }

        function applyData() {
          const data = Object.assign({}, mockData, window.__SIGNAGE_PREVIEW__ || {});
          const palette = data.palette || mockData.palette;
          const resolvedColors = data.resolvedColors || {};
          const root = document.documentElement;

          if (palette.vibrant) root.style.setProperty('--token-vibrant', palette.vibrant);
          if (palette.muted) root.style.setProperty('--token-muted', palette.muted);
          if (palette.lightVibrant) root.style.setProperty('--token-light-vibrant', palette.lightVibrant);
          if (palette.darkVibrant) root.style.setProperty('--token-dark-vibrant', palette.darkVibrant);
          if (palette.lightMuted) root.style.setProperty('--token-light-muted', palette.lightMuted);
          if (palette.darkMuted) root.style.setProperty('--token-dark-muted', palette.darkMuted);

          const badge = document.getElementById('badge');
          const headline = document.getElementById('headline');
          const subtitle = document.getElementById('subtitle');
          const priceMain = document.getElementById('priceMain');
          const priceCents = document.getElementById('priceCents');
          const ctaText = document.getElementById('ctaText');
          const productImage = document.getElementById('productImage');

          const defaults = captureTemplateDefaults(root, priceMain);

          const backgroundColor = pickColorValue(colorConfig.background, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.background.cssVars, backgroundColor);

          const textColor = pickColorValue(colorConfig.text, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.text.cssVars, textColor);

          const subtitleColor = pickColorValue(colorConfig.subtitle, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.subtitle.cssVars, subtitleColor);

          const badgeColor = pickColorValue(colorConfig.badge, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.badge.cssVars, badgeColor);
          const glowColor = badgeColor
            ? 'color-mix(in srgb, ' + badgeColor + ' 40%, transparent 60%)'
            : '';
          applyCssVars(root, '--accent-glow', glowColor);

          const separatorColor = pickColorValue(colorConfig.separator, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.separator.cssVars, separatorColor);

          const priceColor = pickColorValue(colorConfig.price, palette, resolvedColors, defaults);
          applyElementColor(priceMain, priceColor);
          applyElementColor(priceCents, priceColor);

          const hasResolved = Object.keys(resolvedColors).length > 0;
          root.classList.remove('is-dark', 'is-light');
          if (!hasResolved) {
            if (palette.isDark === true) {
              root.classList.add('is-dark');
            } else if (palette.isDark === false) {
              root.classList.add('is-light');
            }
          }

          if (data.badge) {
            badge.textContent = data.badge;
            badge.style.display = 'inline';
          } else {
            badge.style.display = 'none';
          }
          headline.textContent = data.headline || mockData.headline;
          subtitle.textContent = data.subtitle || mockData.subtitle;
          ctaText.textContent = data.cta || mockData.cta;

          const priceStr = normalizePrice(data.price || mockData.price);
          const parts = priceStr.replace(',', '.').split('.');
          priceMain.textContent = parts[0] || '0';
          priceCents.textContent = parts[1] ? ',' + parts[1] : '';

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
