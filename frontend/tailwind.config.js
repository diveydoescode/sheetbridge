/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        paper: "#F3EFE6",
        ink: "#1B2118",
        muted: "#5C6456",
        rule: "#D6CFC0",
        sheet: {
          DEFAULT: "#1F7A4D",
          dim: "#D7EDE0",
        },
        db: {
          DEFAULT: "#215E8C",
          dim: "#D5E4F0",
        },
        conflict: {
          DEFAULT: "#B42318",
          dim: "#F8D7D3",
        },
        merge: {
          DEFAULT: "#8A5A00",
          dim: "#F3E2C4",
        },
      },
      fontFamily: {
        serif: ["\"Source Serif 4\"", "Georgia", "serif"],
        sans: ["\"IBM Plex Sans\"", "system-ui", "sans-serif"],
        mono: ["\"IBM Plex Mono\"", "ui-monospace", "monospace"],
      },
      boxShadow: {
        ledger: "0 1px 0 rgba(27, 33, 24, 0.06), 0 12px 32px rgba(27, 33, 24, 0.06)",
      },
    },
  },
  plugins: [],
};
