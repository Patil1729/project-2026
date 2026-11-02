import UserSummary from '../dashboard/UserSummary';
import ImpactStats from '../dashboard/ImpactStats';
import StateExecutive from './StateExecutive';
import PurposeSection from './PurposeSection';
import Leadership from './Leadership';
import Gallery from '../gallery/Gallery';
import { lazy, Suspense } from "react";
import { usePermissions } from "../../app/usePermissions";
const MaharashtraMap = lazy(() => import("../../components/MaharashtraMap"));
import { Link } from "react-router";
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Building2,
  CheckCircle2,
  ChevronRight,
  FileCheck2,
  FileText,
  HeartHandshake,
  Landmark,
  MapPin,
  MessageSquareText,
  UserRoundCheck,
  Network,
  Scale,
  ShieldCheck,
  Users,
} from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Avatar, ResourceState } from "../../components/ui";
import { humanize, items } from "../../lib/format";

const copy = {
  en: {
    motto: "JUSTICE · PROTECTION · EMPOWERMENT",
    title: "SHIV SENA LEGAL CELL",
    // subtitle: "शिवसेना विधी विभाग",
    community: "One community. A shared commitment to justice.",
    intro:
      "Connecting advocates across Maharashtra. Join your local committee, contribute your expertise, and be part of a stronger legal community.",
    join: "Become a member",
    track: "Track your application",
    membership: "Membership & appointments",
    hint: "A clear path from registration to appointment",
    stats: [
      "Membership applications",
      "Active committees",
      "Filled committee positions",
    ],
    networkLabel: "ACROSS MAHARASHTRA",
    networkTitle: "Every branch. One connected network.",
    networkDescription:
      "Explore the divisions and the organization units connected to them.",
    districts: "Districts in this division",
    noDistricts: "No active districts have been added to this division yet.",
    noNetwork: "Build your organization hierarchy to see the network here.",
    networkAction: "Explore organization",
    division: "divisions",
    district: "districts",
    branch: "organization units",
    leadershipLabel: "OUR INSPIRATION",
    leadershipTitle: "Inspiration & leadership",
    leaders: [
      ["Balasaheb Thackeray", "Hinduhridaysamrat", "Founder, Shiv Sena"],
      ["Anand Dighe", "Dharmaveer", "Inspiration"],
      ["Eknath Shinde", "Hon.", "Shiv Sena leadership"],
    ],
    appointmentsLabel: "OUR PEOPLE",
    appointmentsTitle: "Advocates carrying the work forward.",
    appointmentsDescription:
      "Active appointments from the connected workspace.",
    appointmentsLink: "View appointments",
    noAppointments:
      "Approved appointments will appear here as your network grows.",
    dataError:
      "Workspace figures are unavailable. Check your connection in Settings.",
    live: "Connected workspace figures",
  },
  mr: {
    motto: "न्याय · संरक्षण · सक्षमीकरण",
    title: "शिवसेना विधी विभाग",
    subtitle: "महाराष्ट्रातील वकिलांचे एकत्रित व्यासपीठ",
    community: "एक संघटन. न्यायासाठी एकत्रित प्रयत्न.",
    intro:
      "महाराष्ट्रातील वकिलांना जोडणारे व्यासपीठ. आपल्या स्थानिक समितीत सहभागी व्हा आणि सक्षम विधी संघटनेचा भाग बना.",
    join: "सदस्य बना",
    track: "अर्जाची स्थिती पहा",
    membership: "सदस्यता आणि नियुक्त्या",
    hint: "नोंदणीपासून नियुक्तीपर्यंत एक स्पष्ट प्रक्रिया",
    stats: ["सदस्यत्वाचे अर्ज", "सक्रिय समित्या", "भरलेली पदे"],
    networkLabel: "महाराष्ट्रभर",
    networkTitle: "प्रत्येक शाखा. एक संघटित जाळे.",
    networkDescription:
      "विभाग आणि त्यांच्याशी जोडलेल्या संघटन घटकांची माहिती पहा.",
    districts: "या विभागातील जिल्हे",
    noDistricts: "या विभागात अद्याप सक्रिय जिल्हे जोडलेले नाहीत.",
    noNetwork: "येथे माहिती पाहण्यासाठी संघटन रचना तयार करा.",
    networkAction: "संघटन रचना पहा",
    division: "विभाग",
    district: "जिल्हे",
    branch: "संघटन घटक",
    leadershipLabel: "आमची प्रेरणा",
    leadershipTitle: "प्रेरणास्थान आणि नेतृत्व",
    leaders: [
      ["बाळासाहेब ठाकरे", "हिंदुहृदयसम्राट", "संस्थापक, शिवसेना"],
      ["आनंद दिघे", "धर्मवीर", "प्रेरणास्थान"],
      ["एकनाथ शिंदे", "मा.", "शिवसेना नेतृत्व"],
    ],
    appointmentsLabel: "आपली माणसं",
    appointmentsTitle: "कार्य पुढे नेणारे वकील.",
    appointmentsDescription: "जोडलेल्या प्रणालीतील सक्रिय नियुक्त्या.",
    appointmentsLink: "नियुक्त्या पहा",
    noAppointments: "मंजूर नियुक्त्या येथे दिसतील.",
    dataError: "आकडेवारी उपलब्ध नाही. सेटिंग्जमध्ये जोडणी तपासा.",
    live: "जोडलेल्या प्रणालीतील आकडेवारी",
  },
};
function SectionTitle({ label, title, description, action }) {
  return (
    <div className="portal-section-heading">
      <div>
        <span className="portal-overline">{label}</span>
        <h2>{title}</h2>
        {description && <p>{description}</p>}
      </div>
      {action}
    </div>
  );
}
export default function Home({ language }) {
  const t = copy[language], mr = language === "mr";
  const { user } = useApp();
  const { manage } = usePermissions();
  const appointments = useResource("/advocate-assignments", { active: true, size: 4 }, manage);
  const records = items(appointments.data).filter(a => a.active);
  return (
    <>
      <section className="portal-hero">
        <div className="portal-container hero-content">
          <span className="portal-motto">
            <span />
            {t.motto}
            <span />
          </span>
          <h1>{t.title}</h1>
          <p
            className={`portal-hero-translation ${mr ? "in-english" : ""}`}
            lang="mr"
          >
            {t.subtitle}
          </p>
          <div className="portal-community">
            <Scale size={17} />
            <span>{t.community}</span>
          </div>
          <p className="portal-hero-intro">{t.intro}</p>
          <div className="portal-hero-actions">
            <Link className="portal-button" to={user ? "/applications?view=my&new=1" : "/signup"}>
              <Users size={18} />
              {t.join}
              <ArrowUpRight size={18} />
            </Link>
            <Link
              className="portal-button portal-button-outline"
              to={user ? "/applications?view=my" : "/login"}
            >
              <FileText size={18} />
              {t.track}
            </Link>
            <Link className="portal-button portal-button-outline" to="/help"><MessageSquareText size={18}/>{mr ? 'मदत हवी आहे?' : 'Get help'}</Link>
          </div>
          <span className="portal-hero-caption">
            <ShieldCheck size={14} />
            {t.hint}
          </span>
        </div>
        <span className="hero-watermark hero-watermark-left" aria-hidden="true">
          <Scale />
        </span>
        <span
          className="hero-watermark hero-watermark-right"
          aria-hidden="true"
        >
          <Landmark />
        </span>
      </section>
      <div className="portal-container"><ImpactStats/>{manage && <UserSummary/>}</div>
      <div className="portal-container public-session-invite"><p>{mr ? "नकाशा आणि आमचे कार्य सर्वांसाठी खुले आहे. तुमचे अर्ज आणि कार्यक्षेत्र पाहण्यासाठी प्रवेश करा." : "Explore our map and work. Sign in to view your applications and workspace."}</p><Link className="portal-text-link" to={user ? "/dashboard" : "/login"}>{user ? (mr ? "माझे कार्यक्षेत्र" : "My workspace") : (mr ? "प्रवेश करा" : "Login")}<ArrowRight size={16}/></Link></div>
      <PurposeSection/>
      <section className="portal-section portal-how-it-works" id="how-it-works">
        <div className="portal-container">
          <SectionTitle label={mr ? "कायदेशीर मदत" : "LEGAL SUPPORT"} title={mr ? "कसे कार्य करते" : "How it works"} description={mr ? "तुमच्या प्रश्नापासून निराकरणापर्यंत मार्गदर्शन." : "Guidance from your first concern through to resolution."}/>
          <div className="portal-process-grid">{[
            [MessageSquareText, mr ? "तक्रार नोंदवा" : "Report your issue", mr ? "काय घडले ते लिहा आणि संबंधित कागदपत्रे जोडा." : "Describe what happened and attach any relevant documents."],
            [UserRoundCheck, mr ? "वकिलाची नेमणूक" : "An advocate is assigned", mr ? "तुमच्या क्षेत्रातील किंवा वरिष्ठ समितीतील विद्यमान सदस्याचे मार्गदर्शन मिळवा." : "A current member from your area or a parent committee is assigned to guide you."],
            [CheckCircle2, mr ? "पाठपुरावा आणि निराकरण" : "Track and resolve", mr ? "तक्रार क्रमांकाने स्थिती आणि पुढील कार्यवाही तपासा." : "Follow progress and updates with your ticket ID through to resolution."],
          ].map(([Icon, title, description], i) => <article key={title}><div className="process-card-top"><span className="portal-step-icon"><Icon size={25}/></span><span className="process-number">0{i + 1}</span></div><h3>{title}</h3><p>{description}</p><span className="process-step-label">{mr ? "टप्पा" : "STEP"} 0{i + 1}</span></article>)}</div>
        </div>
      </section>

      <Leadership language={language}/>
      <StateExecutive/>
      <section className="portal-section portal-network" id="network"><div className="portal-container"><SectionTitle label={t.networkLabel} title={mr ? "महाराष्ट्र राज्याचा नकाशा" : "Maharashtra state map"} description={mr ? "जिल्हा निवडा किंवा नकाशा मोठा करून पहा." : "Select a district or zoom in to explore Maharashtra."}/><Suspense fallback={<p role="status">{mr ? "नकाशा लोड होत आहे…" : "Loading map…"}</p>}><MaharashtraMap/></Suspense></div></section>
      <Gallery preview/>
      {manage && <>
      <section className="portal-section portal-appointments">
        <div className="portal-container">
          <SectionTitle
            label={t.appointmentsLabel}
            title={t.appointmentsTitle}
            description={t.appointmentsDescription}
            action={
              <Link to="/appointments" className="portal-text-link">
                {t.appointmentsLink}
                <ArrowUpRight size={17} />
              </Link>
            }
          />
          <ResourceState resource={appointments}>
            {records.length ? (
              <div className="portal-appointment-grid">
                {records.map((a, i) => (
                  <Link
                    to="/appointments"
                    className="portal-appointment-card"
                    key={a.id}
                  >
                    <Avatar name={a.advocateName} size="large" index={i} />
                    <span className="portal-appointment-check">
                      <CheckCircle2 size={16} />
                    </span>
                    <h3>{a.advocateName}</h3>
                    <span className="appointment-position">
                      {humanize(a.positionType)}
                    </span>
                    <p>{a.committeeName}</p>
                    <span className="appointment-detail">
                      {mr ? "नियुक्ती पहा" : "View appointment"}
                      <ArrowUpRight size={13} />
                    </span>
                  </Link>
                ))}
              </div>
            ) : (
              <div className="portal-empty-network">{t.noAppointments}</div>
            )}
          </ResourceState>
        </div>
      </section>
      </>}

    </>
  );
}
