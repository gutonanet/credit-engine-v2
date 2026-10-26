import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#eef6ff",
          600: "#1d4ed8",
          900: "#0f172a",
        },
      },
    },
  },
  plugins: [],
};

export default config;
