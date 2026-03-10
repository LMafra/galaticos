/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./resources/templates/**/*.html",
    "./src-cljs/**/*.{cljs,cljc,clj}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          maroon: "#820000",
          gold: "#FFD500",
        },
      },
    },
  },
  darkMode: "class",
  plugins: [],
};
