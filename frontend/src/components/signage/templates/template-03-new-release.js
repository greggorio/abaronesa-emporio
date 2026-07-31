import { mockImage } from "./shared";

export const newRelease = {
  id: "new-release",
  title: "Template 03 - New Release",
  subtitle: "Layout de lançamento com brilho, faixa de novidade e CTA forte.",
  srcdoc: `<!DOCTYPE html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <title>New Release Template</title>
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
        --bg-main: color-mix(in srgb, var(--token-dark-muted) 92%, black 8%);
        --bg-radial: radial-gradient(ellipse 65% 50% at 35% 50%, 
          color-mix(in srgb, var(--token-vibrant) 25%, transparent 75%) 0%, 
          transparent 65%);
        --glow-strong: color-mix(in srgb, var(--token-vibrant) 70%, transparent 30%);
        --glow-soft: color-mix(in srgb, var(--token-light-vibrant) 40%, transparent 60%);
        --text-main: var(--token-light-muted);
        --text-soft: color-mix(in srgb, var(--token-light-muted) 70%, var(--token-muted) 30%);
        --accent: var(--token-vibrant);
        --accent-bright: var(--token-light-vibrant);
        --ribbon-bg: linear-gradient(135deg, var(--token-vibrant) 0%, var(--token-light-vibrant) 100%);
      }
      html, body {
        width: 1920px;
        height: 1080px;
        overflow: hidden;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
        background: var(--bg-main);
        color: var(--text-main);
      }
      .frame {
        position: relative;
        width: 100%;
        height: 100%;
        overflow: hidden;
        background: 
          var(--bg-radial),
          linear-gradient(135deg, 
            var(--bg-main) 0%, 
            color-mix(in srgb, var(--bg-main) 85%, var(--token-muted) 15%) 100%
          );
      }

      /* Ribbon NOVO no canto superior direito */
      .ribbon-container {
        position: absolute;
        top: 0;
        right: 0;
        width: 300px;
        height: 300px;
        overflow: hidden;
        z-index: 10;
        pointer-events: none;
      }
      .ribbon {
        position: absolute;
        top: 60px;
        right: -75px;
        width: 350px;
        height: 85px;
        background: var(--ribbon-bg);
        color: var(--token-dark-muted);
        font-weight: 900;
        font-size: 32px;
        letter-spacing: 8px;
        text-transform: uppercase;
        display: flex;
        align-items: center;
        justify-content: center;
        transform: rotate(45deg);
        box-shadow: 
          0 8px 24px rgba(0, 0, 0, 0.4),
          inset 0 1px 0 rgba(255, 255, 255, 0.3);
        animation: ribbonPulse 3s ease-in-out infinite;
      }

      /* Partículas brilhantes animadas */
      .particles {
        position: absolute;
        inset: 0;
        pointer-events: none;
        z-index: 1;
      }
      .particle {
        position: absolute;
        width: 8px;
        height: 8px;
        background: var(--accent);
        border-radius: 50%;
        opacity: 0;
        box-shadow: 0 0 20px var(--glow-strong);
        animation: particleFloat 4s ease-in-out infinite;
      }
      .particle:nth-child(1) { left: 15%; top: 20%; animation-delay: 0s; }
      .particle:nth-child(2) { left: 25%; top: 60%; animation-delay: 0.8s; }
      .particle:nth-child(3) { left: 38%; top: 35%; animation-delay: 1.5s; }
      .particle:nth-child(4) { right: 30%; top: 25%; animation-delay: 0.4s; }
      .particle:nth-child(5) { right: 20%; top: 65%; animation-delay: 1.2s; }
      .particle:nth-child(6) { right: 40%; top: 45%; animation-delay: 2s; }

      /* Raios de luz */
      .light-rays {
        position: absolute;
        left: -8%;
        top: -10%;
        width: 70%;
        height: 120%;
        background: 
          linear-gradient(75deg, transparent 40%, var(--glow-soft) 50%, transparent 60%),
          linear-gradient(85deg, transparent 45%, var(--glow-soft) 55%, transparent 65%);
        opacity: 0.3;
        animation: raysRotate 8s linear infinite;
        transform-origin: center;
        pointer-events: none;
        z-index: 0;
        mask-image: radial-gradient(ellipse 80% 70% at 35% 50%, black 0%, transparent 70%);
        -webkit-mask-image: radial-gradient(ellipse 80% 70% at 35% 50%, black 0%, transparent 70%);
      }

      /* Container principal - Layout invertido */
      .main-container {
        position: relative;
        width: 100%;
        height: 100%;
        display: grid;
        grid-template-columns: 55% 45%;
        align-items: center;
        padding: 0 80px;
        gap: 60px;
        z-index: 2;
      }

      /* Área do produto (ESQUERDA) */
      .product-area {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        animation: productEntrance 1s cubic-bezier(0.34, 1.56, 0.64, 1) both;
      }

      .product-glow {
        position: absolute;
        width: 700px;
        height: 700px;
        border-radius: 50%;
        background: radial-gradient(circle, var(--glow-strong) 0%, transparent 70%);
        filter: blur(60px);
        animation: glowPulse 4s ease-in-out infinite;
        z-index: 0;
      }

      .product-ring {
        position: absolute;
        width: 650px;
        height: 650px;
        border: 2px solid var(--accent);
        border-radius: 50%;
        opacity: 0.3;
        animation: ringRotate 20s linear infinite;
        z-index: 0;
      }
      .product-ring::before {
        content: '';
        position: absolute;
        top: -6px;
        left: 50%;
        transform: translateX(-50%);
        width: 12px;
        height: 12px;
        background: var(--accent);
        border-radius: 50%;
        box-shadow: 0 0 20px var(--accent);
      }

      .product-image {
        position: relative;
        width: 600px;
        height: 600px;
        object-fit: contain;
        filter: drop-shadow(0 30px 60px rgba(0, 0, 0, 0.4));
        animation: productFloat 5s ease-in-out infinite;
        z-index: 1;
      }

      /* Área de conteúdo (DIREITA) */
      .content-area {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: 24px;
        animation: contentSlide 1s ease-out 0.3s both;
      }

      .badge-new {
        display: inline-flex;
        align-items: center;
        gap: 16px;
        font-size: 18px;
        font-weight: 800;
        letter-spacing: 6px;
        text-transform: uppercase;
        color: var(--accent);
        opacity: 0;
        animation: fadeInUp 0.7s ease-out 0.5s forwards;
      }
      .badge-new::before {
        content: '';
        width: 50px;
        height: 3px;
        background: var(--accent);
        box-shadow: 0 0 10px var(--accent);
        animation: lineExpand 0.6s ease-out 0.7s forwards;
        transform: scaleX(0);
        transform-origin: left;
      }

      .headline {
        font-size: 110px;
        font-weight: 900;
        line-height: 0.95;
        letter-spacing: -4px;
        text-shadow: 
          0 4px 12px rgba(0, 0, 0, 0.3),
          0 12px 40px rgba(0, 0, 0, 0.2);
        background: linear-gradient(135deg, var(--text-main) 0%, var(--accent-bright) 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
        opacity: 0;
        animation: fadeInUp 0.9s ease-out 0.7s forwards;
      }

      .subtitle {
        font-size: 34px;
        font-weight: 400;
        line-height: 1.4;
        color: var(--text-soft);
        max-width: 650px;
        opacity: 0;
        animation: fadeInUp 0.8s ease-out 0.9s forwards;
      }

      .price-container {
        display: flex;
        align-items: baseline;
        gap: 12px;
        margin: 8px 0;
        opacity: 0;
        animation: fadeInUp 0.7s ease-out 1.1s forwards;
      }
      .price-currency {
        font-size: 40px;
        font-weight: 700;
        color: var(--accent);
      }
      .price-main {
        font-size: 96px;
        font-weight: 900;
        color: var(--text-main);
        letter-spacing: -4px;
        line-height: 1;
        text-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
      }
      .price-cents {
        font-size: 48px;
        font-weight: 700;
        color: var(--text-main);
        margin-bottom: 8px;
      }

      .cta-button {
        display: inline-flex;
        align-items: center;
        gap: 20px;
        background: var(--ribbon-bg);
        color: var(--token-dark-muted);
        font-size: 28px;
        font-weight: 800;
        text-decoration: none;
        padding: 28px 56px;
        border-radius: 16px;
        width: fit-content;
        box-shadow: 
          0 16px 40px color-mix(in srgb, var(--accent) 50%, transparent 50%),
          inset 0 2px 0 rgba(255, 255, 255, 0.4);
        opacity: 0;
        animation: 
          fadeInUp 0.6s ease-out 1.3s forwards,
          ctaPulse 2.5s ease-in-out 2s infinite;
      }
      .cta-button svg {
        width: 28px;
        height: 28px;
      }

      /* Animações */
      @keyframes ribbonPulse {
        0%, 100% { 
          filter: brightness(1);
          transform: rotate(45deg) scale(1);
        }
        50% { 
          filter: brightness(1.15);
          transform: rotate(45deg) scale(1.02);
        }
      }

      @keyframes particleFloat {
        0%, 100% {
          opacity: 0;
          transform: translateY(0) scale(1);
        }
        25%, 75% {
          opacity: 1;
        }
        50% {
          opacity: 1;
          transform: translateY(-30px) scale(1.5);
        }
      }

      @keyframes raysRotate {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }

      @keyframes productEntrance {
        0% {
          opacity: 0;
          transform: scale(0.8) translateX(-100px);
        }
        100% {
          opacity: 1;
          transform: scale(1) translateX(0);
        }
      }

      @keyframes contentSlide {
        0% {
          opacity: 0;
          transform: translateX(80px);
        }
        100% {
          opacity: 1;
          transform: translateX(0);
        }
      }

      @keyframes glowPulse {
        0%, 100% {
          opacity: 0.6;
          transform: scale(1);
        }
        50% {
          opacity: 1;
          transform: scale(1.1);
        }
      }

      @keyframes ringRotate {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }

      @keyframes productFloat {
        0%, 100% {
          transform: translateY(0) scale(1);
        }
        50% {
          transform: translateY(-15px) scale(1.02);
        }
      }

      @keyframes fadeInUp {
        0% {
          opacity: 0;
          transform: translateY(30px);
        }
        100% {
          opacity: 1;
          transform: translateY(0);
        }
      }

      @keyframes lineExpand {
        0% {
          transform: scaleX(0);
        }
        100% {
          transform: scaleX(1);
        }
      }

      @keyframes ctaPulse {
        0%, 100% {
          transform: scale(1);
          box-shadow: 
            0 16px 40px color-mix(in srgb, var(--accent) 50%, transparent 50%),
            inset 0 2px 0 rgba(255, 255, 255, 0.4);
        }
        50% {
          transform: scale(1.05);
          box-shadow: 
            0 20px 50px color-mix(in srgb, var(--accent) 65%, transparent 35%),
            inset 0 2px 0 rgba(255, 255, 255, 0.5);
        }
      }
    </style>
  </head>
  <body>
    <div class="frame">
      <!-- Ribbon NOVO -->
      <div class="ribbon-container">
        <div class="ribbon" id="ribbon">NOVO</div>
      </div>

      <!-- Raios de luz -->
      <div class="light-rays"></div>

      <!-- Partículas -->
      <div class="particles">
        <div class="particle"></div>
        <div class="particle"></div>
        <div class="particle"></div>
        <div class="particle"></div>
        <div class="particle"></div>
        <div class="particle"></div>
      </div>

      <!-- Container principal -->
      <div class="main-container">
        <!-- Produto à ESQUERDA -->
        <div class="product-area">
          <div class="product-glow"></div>
          <div class="product-ring"></div>
          <img class="product-image" id="productImage" src="${mockImage}" alt="Product" />
        </div>

        <!-- Conteúdo à DIREITA -->
        <div class="content-area">
          <div class="badge-new" id="badge">LANÇAMENTO</div>
          
          <h1 class="headline" id="headline">Alcachofras flambadas</h1>
          
          <p class="subtitle" id="subtitle">Esse inverno, peça um desses!</p>
          
          <div class="price-container">
            <span class="price-currency">R$</span>
            <span class="price-main" id="priceMain">65</span>
            <span class="price-cents" id="priceCents">,00</span>
          </div>
          
          <a class="cta-button" href="#" id="ctaButton">
            <span id="ctaText">Experimente já</span>
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
            </svg>
          </a>
        </div>
      </div>
    </div>

    <script>
      (function() {
        const mockData = {
          badge: "LANÇAMENTO",
          headline: "Alcachofras flambadas",
          subtitle: "Esse inverno, peça um desses!",
          cta: "Experimente já",
          price: "65,00",
          image: "${mockImage}",
          palette: {
            vibrant: "#e0b15a",
            muted: "#7a6a52",
            lightVibrant: "#f2d19b",
            darkVibrant: "#a0782b",
            lightMuted: "#e7ddcf",
            darkMuted: "#2b241c",
            isDark: true
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
            cssVars: ['--brand-primary', '--accent', '--accent-bright'],
          },
          separator: {
            resolvedKeys: ['separator'],
            paletteKeys: ['muted'],
            cacheKey: 'separator',
            safe: '#000000',
            cssVars: '--separator',
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
              accentBright: computed.getPropertyValue('--accent-bright').trim(),
              separator: computed.getPropertyValue('--separator').trim(),
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
          const radialBackground = backgroundColor
            ? 'radial-gradient(ellipse 65% 50% at 35% 50%, color-mix(in srgb, ' + backgroundColor + ' 25%, transparent 75%) 0%, transparent 65%)'
            : '';
          applyCssVars(root, '--bg-radial', radialBackground);

          const textColor = pickColorValue(colorConfig.text, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.text.cssVars, textColor);

          const subtitleColor = pickColorValue(colorConfig.subtitle, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.subtitle.cssVars, subtitleColor);

          const badgeColor = pickColorValue(colorConfig.badge, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.badge.cssVars, badgeColor);
          const ribbonColor = badgeColor
            ? 'linear-gradient(135deg, ' + badgeColor + ' 0%, color-mix(in srgb, ' + badgeColor + ' 65%, white 35%) 100%)'
            : '';
          applyCssVars(root, '--ribbon-bg', ribbonColor);

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
            badge.style.display = 'inline-flex';
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
