import { mockImage } from './shared';

export const heroPromo = {
    id: "promo-hero",
    title: "Template 01 - Hero Promo",
    subtitle: "Full-bleed com overlays e CTA destacado.",
    srcdoc: `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <title>Product Template</title>
    <style>
      *, *::before, *::after {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
      }
      :root {
        --token-vibrant: #ff6b35;
        --token-muted: #8b7355;
        --token-light-vibrant: #ffa07a;
        --token-dark-vibrant: #c44d1a;
        --token-light-muted: #d4c4b0;
        --token-dark-muted: #4a3f35;
        --bg-main: color-mix(in srgb, var(--token-dark-muted) 85%, black 15%);
        --bg-secondary: color-mix(in srgb, var(--token-dark-muted) 60%, var(--token-muted) 40%);
        --bg-overlay: color-mix(in srgb, var(--token-dark-vibrant) 20%, transparent 80%);
        --text-main: var(--token-light-muted);
        --text-soft: color-mix(in srgb, var(--token-light-muted) 70%, var(--token-muted) 30%);
        --brand-primary: var(--token-vibrant);
        --brand-accent: var(--token-light-vibrant);
        --brand-shadow: color-mix(in srgb, var(--token-dark-vibrant) 50%, transparent 50%);
        --border-soft: color-mix(in srgb, var(--token-muted) 30%, transparent 70%);
        --glow: color-mix(in srgb, var(--token-vibrant) 40%, transparent 60%);
        --safe-area: 80px;
      }
      .light-mode {
        --bg-main: color-mix(in srgb, var(--token-light-muted) 90%, white 10%);
        --bg-secondary: color-mix(in srgb, var(--token-light-vibrant) 20%, white 80%);
        --bg-overlay: color-mix(in srgb, var(--token-light-muted) 30%, transparent 70%);
        --text-main: var(--token-dark-muted);
        --text-soft: color-mix(in srgb, var(--token-dark-muted) 70%, var(--token-muted) 30%);
      }
      body {
        width: 1920px;
        height: 1080px;
        overflow: hidden;
        font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        background: var(--bg-main);
        color: var(--text-main);
      }
      .container {
        width: 100%;
        height: 100%;
        padding: var(--safe-area);
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 60px;
        position: relative;
      }
      .bg-gradient {
        position: absolute;
        inset: 0;
        background:
          radial-gradient(ellipse 80% 60% at 75% 50%, var(--bg-overlay), transparent),
          radial-gradient(ellipse 50% 80% at 20% 80%, var(--glow), transparent);
        pointer-events: none;
        z-index: 0;
      }
      .bg-pattern {
        position: absolute;
        inset: 0;
        opacity: 0.03;
        background-image:
          linear-gradient(var(--border-soft) 1px, transparent 1px),
          linear-gradient(90deg, var(--border-soft) 1px, transparent 1px);
        background-size: 60px 60px;
        pointer-events: none;
        z-index: 0;
      }
      .content {
        display: flex;
        flex-direction: column;
        justify-content: center;
        z-index: 1;
        padding-left: 40px;
      }
      .badge {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        background: var(--brand-primary);
        color: var(--bg-main);
        padding: 10px 24px;
        border-radius: 100px;
        font-size: 14px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 2px;
        width: fit-content;
        margin-bottom: 32px;
        animation: slideIn 0.6s ease-out both;
        box-shadow: 0 4px 24px var(--brand-shadow);
      }
      .badge:empty {
        display: none;
      }
      .badge::before {
        content: "";
        width: 8px;
        height: 8px;
        background: currentColor;
        border-radius: 50%;
        animation: pulse 2s ease-in-out infinite;
      }
      .badge:empty::before {
        display: none;
      }
      .headline {
        font-size: 86px;
        font-weight: 800;
        line-height: 1.05;
        letter-spacing: -3px;
        margin-bottom: 28px;
        animation: slideIn 0.6s ease-out 0.1s both;
        background: linear-gradient(135deg, var(--text-main) 0%, var(--brand-accent) 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
      }
      .subtitle {
        font-size: 26px;
        line-height: 1.6;
        color: var(--text-soft);
        max-width: 540px;
        margin-bottom: 48px;
        animation: slideIn 0.6s ease-out 0.2s both;
      }
      .price-row {
        display: flex;
        align-items: baseline;
        gap: 20px;
        margin-bottom: 40px;
        animation: slideIn 0.6s ease-out 0.3s both;
      }
      .price {
        font-size: 56px;
        font-weight: 800;
        color: var(--brand-primary);
        letter-spacing: -2px;
      }
      .price-label {
        font-size: 18px;
        color: var(--text-soft);
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      .cta-button {
        display: inline-flex;
        align-items: center;
        gap: 16px;
        background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-accent) 100%);
        color: var(--bg-main);
        padding: 24px 56px;
        border-radius: 16px;
        font-size: 22px;
        font-weight: 700;
        text-decoration: none;
        width: fit-content;
        animation: slideIn 0.6s ease-out 0.4s both;
        box-shadow:
          0 8px 32px var(--brand-shadow),
          inset 0 1px 0 rgba(255, 255, 255, 0.2);
      }
      .cta-button svg {
        width: 24px;
        height: 24px;
      }
      .image-section {
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1;
        position: relative;
      }
      .image-wrapper {
        position: relative;
        animation: floatIn 0.8s ease-out 0.2s both;
      }
      .image-glow {
        position: absolute;
        inset: -40px;
        background: radial-gradient(circle, var(--glow) 0%, transparent 70%);
        filter: blur(60px);
        z-index: 0;
        animation: glowPulse 4s ease-in-out infinite;
      }
      .product-image {
        position: relative;
        z-index: 1;
        max-width: 700px;
        max-height: 700px;
        width: auto;
        height: auto;
        object-fit: contain;
        border-radius: 0;
        box-shadow: none;
        background: transparent;
        filter: drop-shadow(0 24px 48px rgba(0, 0, 0, 0.22));
        transition: opacity 0.3s ease;
      }
      .product-image:not([src]),
      .product-image[src=""] {
        opacity: 0;
      }
      .image-decoration {
        position: absolute;
        width: 120%;
        height: 120%;
        top: -10%;
        left: -10%;
        border: 1px solid color-mix(in srgb, var(--brand-primary) 65%, transparent 85%);
        border-radius: 40px;
        z-index: 0;
        animation: rotate 20s linear infinite;
      }
      .image-decoration::before {
        content: "";
        position: absolute;
        top: -8px;
        left: 50%;
        transform: translateX(-50%);
        width: 16px;
        height: 16px;
        background: var(--brand-primary);
        border-radius: 50%;
        box-shadow: 0 0 20px var(--glow);
      }
      .corner-decoration {
        position: absolute;
        width: 200px;
        height: 200px;
        border: 2px solid var(--border-soft);
        z-index: 0;
      }
      .corner-decoration.top-left {
        top: var(--safe-area);
        left: var(--safe-area);
        border-right: none;
        border-bottom: none;
        border-radius: 24px 0 0 0;
      }
      .corner-decoration.bottom-right {
        bottom: var(--safe-area);
        right: var(--safe-area);
        border-left: none;
        border-top: none;
        border-radius: 0 0 24px 0;
      }
      @keyframes slideIn {
        from {
          opacity: 0;
          transform: translateY(30px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
      @keyframes floatIn {
        from {
          opacity: 0;
          transform: scale(0.9) translateY(40px);
        }
        to {
          opacity: 1;
          transform: scale(1) translateY(0);
        }
      }
      @keyframes pulse {
        0%, 100% { opacity: 1; transform: scale(1); }
        50% { opacity: 0.5; transform: scale(1.2); }
      }
      @keyframes glowPulse {
        0%, 100% { opacity: 0.6; transform: scale(1); }
        50% { opacity: 1; transform: scale(1.1); }
      }
      @keyframes rotate {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
      .particles {
        position: absolute;
        inset: 0;
        overflow: hidden;
        pointer-events: none;
        z-index: 0;
      }
      .particle {
        position: absolute;
        width: 6px;
        height: 6px;
        background: var(--brand-accent);
        border-radius: 50%;
        opacity: 0.4;
        animation: float 8s ease-in-out infinite;
      }
      .particle:nth-child(1) { left: 10%; top: 20%; animation-delay: 0s; }
      .particle:nth-child(2) { left: 80%; top: 30%; animation-delay: 1s; }
      .particle:nth-child(3) { left: 60%; top: 70%; animation-delay: 2s; }
      .particle:nth-child(4) { left: 30%; top: 80%; animation-delay: 3s; }
      .particle:nth-child(5) { left: 90%; top: 60%; animation-delay: 4s; }
      @keyframes float {
        0%, 100% { transform: translateY(0) scale(1); opacity: 0.4; }
        50% { transform: translateY(-30px) scale(1.5); opacity: 0.8; }
      }
    </style>
  </head>
  <body style="--token-vibrant: #e94560; --token-muted: #7f8c8d; --token-light-vibrant: #ff6b8a; --token-dark-vibrant: #c0392b; --token-light-muted: #ecf0f1; --token-dark-muted: #1a1a2e;">
    <div class="container">
      <div class="bg-gradient"></div>
      <div class="bg-pattern"></div>
      <div class="particles">
        <div class="particle"></div>
        <div class="particle"></div>
        <div class="particle"></div>
        <div class="particle"></div>
        <div class="particle"></div>
      </div>
      <div class="corner-decoration top-left"></div>
      <div class="corner-decoration bottom-right"></div>
      <div class="content">
        <div class="badge" id="badge">Best Seller</div>
        <h1 class="headline" id="headline">Premium Wireless Headphones</h1>
        <p class="subtitle" id="subtitle">Immerse yourself in crystal-clear sound with active noise cancellation and 40-hour battery life.</p>
        <div class="price-row">
          <span class="price" id="price">$349</span>
        </div>
        <a href="#" class="cta-button" id="ctaButton">
          <span id="ctaText">Order Now</span>
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
          </svg>
        </a>
      </div>
      <div class="image-section">
        <div class="image-wrapper">
          <div class="image-glow"></div>
          <div class="image-decoration"></div>
          <img class="product-image" id="productImage" src="${mockImage}" alt="Premium Wireless Headphones" />
        </div>
      </div>
    </div>
    <script>
      (function() {
        const mockData = {
          badge: "Best Seller",
          headline: "Premium Wireless Headphones",
          subtitle: "Immerse yourself in crystal-clear sound with active noise cancellation and 40-hour battery life.",
          cta: "Order Now",
          price: "$349",
          image: "${mockImage}",
          palette: {
            vibrant: "#e94560",
            muted: "#7f8c8d",
            lightVibrant: "#ff6b8a",
            darkVibrant: "#c0392b",
            lightMuted: "#ecf0f1",
            darkMuted: "#1a1a2e",
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
            cssVars: '--brand-primary',
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
          if (/^R\$\s*/i.test(str)) return str;
          if (str.startsWith('$')) return 'R$ ' + str.replace(/^\$\s*/, '');
          return 'R$ ' + str;
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
          const price = document.getElementById('price');
          const ctaText = document.getElementById('ctaText');
          const productImage = document.getElementById('productImage');

          const defaults = captureTemplateDefaults(root, price);

          const backgroundColor = pickColorValue(colorConfig.background, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.background.cssVars, backgroundColor);

          const textColor = pickColorValue(colorConfig.text, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.text.cssVars, textColor);

          const subtitleColor = pickColorValue(colorConfig.subtitle, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.subtitle.cssVars, subtitleColor);

          const badgeColor = pickColorValue(colorConfig.badge, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.badge.cssVars, badgeColor);

          const separatorColor = pickColorValue(colorConfig.separator, palette, resolvedColors, defaults);
          applyCssVars(root, colorConfig.separator.cssVars, separatorColor);

          const priceColor = pickColorValue(colorConfig.price, palette, resolvedColors, defaults);
          applyElementColor(price, priceColor);

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
          price.textContent = normalizePrice(data.price || mockData.price);
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
</html>`
  };
