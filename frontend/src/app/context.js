import { createContext } from "react";

// Keep context identity stable while Vite refreshes provider or data modules.
export const Context = createContext(null);
