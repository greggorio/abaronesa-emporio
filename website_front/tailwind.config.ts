import type { Config } from "tailwindcss";

export default {
  darkMode: ["class"],
  content: ["./pages/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}", "./app/**/*.{ts,tsx}", "./src/**/*.{ts,tsx}"],
  prefix: "",
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: {
        "2xl": "1400px",
      },
    },
    extend: {
      fontFamily: {
        display: ['Bebas Neue', 'sans-serif'],
        body: ['Inter', 'sans-serif'],
        bebas: ['Bebas Neue', 'cursive'],
        inter: ['Inter', 'sans-serif'],
      },
      colors: {
        'forest-green': 'hsl(var(--forest-green))',
        'forest-dark': 'hsl(var(--forest-dark))',
        'coral-accent': 'hsl(var(--coral-accent))',
        cream: 'hsl(var(--cream))',
        'warm-beige': 'hsl(var(--warm-beige))',
        white: 'hsl(var(--white))',
        'soft-white': 'hsl(var(--soft-white))',
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        button: {
          primary: {
            DEFAULT: "hsl(var(--button-primary-bg))",
            foreground: "hsl(var(--button-primary-text))",
          },
          secondary: {
            DEFAULT: "hsl(var(--button-secondary-bg))",
            foreground: "hsl(var(--button-secondary-text))",
            border: "hsl(var(--button-secondary-border))",
          },
        },
        hero: {
          overlay: "hsl(var(--hero-overlay-color))",
          title: "hsl(var(--hero-title-text-color))",
          subtitle: "hsl(var(--hero-subtitle-text-color))",
        },
        about: {
          text: "hsl(var(--about-text-color))",
          card: {
            text: "hsl(var(--about-card-text))",
          },
        },
        contact: {
          card: {
            text: "hsl(var(--contact-card-text))",
          },
        },
        mesa: {
          text: "hsl(var(--mesa-text))",
        },
        header: {
          text: "hsl(var(--header-text-color))",
        },
        viking: {
          charcoal: "hsl(var(--viking-charcoal))",
          red: "hsl(var(--viking-red))",
          gold: "hsl(var(--viking-gold))",
          bone: "hsl(var(--viking-bone))",
          "accent-gold": "#D4A75A",
        },
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      keyframes: {
        "accordion-down": {
          from: {
            height: "0",
          },
          to: {
            height: "var(--radix-accordion-content-height)",
          },
        },
        "accordion-up": {
          from: {
            height: "var(--radix-accordion-content-height)",
          },
          to: {
            height: "0",
          },
        },
        "fade-in": {
          "0%": { opacity: "0", transform: "translateY(20px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "slide-up": {
          "0%": { opacity: "0", transform: "translateY(30px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "glow": {
          "0%, 100%": { boxShadow: "0 0 20px hsl(15 55% 68% / 0.3)" },
          "50%": { boxShadow: "0 0 40px hsl(15 55% 68% / 0.6)" },
        },
      },
      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
        "fade-in": "fade-in 0.6s ease-out",
        "slide-up": "slide-up 0.8s ease-out",
        "glow": "glow 2s ease-in-out infinite",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
} satisfies Config;
