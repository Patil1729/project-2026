import { BookOpen, HeartHandshake, Scale, ShieldCheck, Users } from 'lucide-react';
import { useLocale } from '../../lib/i18n';
import { purposeContent } from '../../lib/purposeContent';

export default function PurposeSection() {
  const language = useLocale(), copy = purposeContent[language];
  return <section className="portal-section portal-purpose" id="about"><div className="portal-container">
    <div className="portal-section-heading"><div><span className="portal-overline">{language === 'mr' ? 'विधी विभागाविषयी' : 'ABOUT THE LEGAL CELL'}</span><h2>{copy.title}</h2>
      {language === 'en' && <p className="purpose-marathi-title" lang="mr">विभागाचा विस्तृत परिचय</p>}
    </div></div>
    <div className="purpose-introduction-grid">{copy.cards.map(([title, text], index) => {
      const Icon = [Scale, HeartHandshake, ShieldCheck][index];
      return <article className="purpose-introduction-card" key={title}><span className="portal-card-icon"><Icon size={24}/></span><h3>{title}</h3><p>{text}</p></article>;
    })}</div>
    <div className="purpose-objectives"><h3>{copy.objectivesTitle}</h3><div className="purpose-objectives-grid">{copy.objectives.map(([title, text], index) => {
      const Icon = [BookOpen, HeartHandshake, ShieldCheck, Users][index];
      return <article key={title}><span className="portal-value-icon"><Icon size={22}/></span><div><h4>{title}</h4><p>{text}</p></div></article>;
    })}</div></div>
  </div></section>;
}
