import { Faq } from './components/Faq';
import { Footer } from './components/Footer';
import { Hero } from './components/Hero';
import { HowItWorks } from './components/HowItWorks';
import { Nav } from './components/Nav';
import { Partners } from './components/Partners';
import { Safety } from './components/Safety';
import { Sellers } from './components/Sellers';
import { Services } from './components/Services';
import { Stats } from './components/Stats';
import { Ticker } from './components/Ticker';
import { useRevealOnScroll } from './lib/motion';

export default function App() {
  useRevealOnScroll();
  return (
    <>
      <Nav />
      <main id="main">
        <Hero />
        <Ticker />
        <Services />
        <HowItWorks />
        <Safety />
        <Stats />
        <Partners />
        <Sellers />
        <Faq />
      </main>
      <Footer />
    </>
  );
}
