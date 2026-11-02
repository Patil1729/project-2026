import { t, useLocale } from "../lib/i18n";
import { useEffect, useRef, useState } from 'react';
import { Facebook, Instagram, Youtube } from 'lucide-react';

// Platform homepages are defaults; set your organisation's actual profiles in .env.
const socials = [['Facebook', Facebook, (import.meta.env.VITE_SOCIAL_FACEBOOK || 'https://www.facebook.com/')], ['Instagram', Instagram, (import.meta.env.VITE_SOCIAL_INSTAGRAM || 'https://www.instagram.com/')], ['YouTube', Youtube, (import.meta.env.VITE_SOCIAL_YOUTUBE || 'https://www.youtube.com/')], ['X', () => <svg viewBox="0 0 24 24" width="19" height="19" fill="currentColor" aria-hidden="true"><path d="M18.9 2H22l-6.8 7.8L23.2 22h-6.3L12 14.6 5.5 22H2.3l7.9-9L1 2h6.5l5.9 7.9L18.9 2ZM17.8 20h1.7L6.6 4H4.8l13 16Z" /></svg>, (import.meta.env.VITE_SOCIAL_X || 'https://x.com/')]];
export function SocialLinks() {
  useLocale();
  return <nav className="social-links" aria-label={t("Social media")}>{socials.map(([name, Icon, url]) => {
      let valid = false;
      try {
        valid = new URL(url).protocol === 'https:';
      } catch {/* Unconfigured profiles remain disabled. */}
      return valid ? <a key={name} href={url} target="_blank" rel="noopener noreferrer" aria-label={t(name)} title={t(name)}><Icon size={19} /></a> : <span key={name} aria-label={t(name + ': profile link not configured')} title={t(name + ': profile link not configured')} aria-disabled="true"><Icon size={19} /></span>;
    })}</nav>;
}
export function PartyCursor() {
  useLocale();
  const ref = useRef();
  const [enabled, setEnabled] = useState(() => localStorage.getItem('legalcell.cursor') !== 'off');
  useEffect(() => {
    if (!enabled || !matchMedia('(pointer:fine)').matches || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const move = e => {
      const el = ref.current;
      if (el) {
        el.style.transform = `translate3d(${e.clientX + 18}px,${e.clientY + 18}px,0)`;
        el.style.opacity = '1';
      }
    };
    const hide = () => {
      if (ref.current) ref.current.style.opacity = '0';
    };
    window.addEventListener('pointermove', move);
    document.addEventListener('pointerleave', hide);
    return () => {
      window.removeEventListener('pointermove', move);
      document.removeEventListener('pointerleave', hide);
    };
  }, [enabled]);
  return <><button className="cursor-toggle" type="button" aria-pressed={enabled} onClick={() => setEnabled(v => {
      localStorage.setItem('legalcell.cursor', v ? 'off' : 'on');
      return !v;
    })}>{t("Party symbol cursor")}</button>
    {enabled && <img ref={ref} className="party-cursor party-emblem-cursor" src="/images/shiv-sena-emblem.png" alt="" aria-hidden="true" draggable="false"/>}</>;
}
