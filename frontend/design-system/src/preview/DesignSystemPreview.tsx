import {
  Bike,
  Bell as BellIcon,
  Calendar,
  CheckCircle2,
  ChevronRight,
  Home,
  MapPin,
  Package,
  PlusCircle,
  Receipt,
  Send,
  Siren,
  User,
  UtensilsCrossed,
  Wallet as WalletIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { AmountText } from '../components/AmountText';
import { BottomNavBar } from '../components/BottomNavBar';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { IconCircle, type IconCircleColor } from '../components/IconCircle';
import { ListRow } from '../components/ListRow';
import { StatusBadge } from '../components/StatusBadge';
import { TopHeader } from '../components/TopHeader';
import { tokens } from '../tokens';

function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <section className="mb-12">
      <h2 className="font-heading text-xl font-semibold text-text-primary">{title}</h2>
      {description && <p className="mt-1 text-sm text-text-secondary">{description}</p>}
      <div className="mt-5">{children}</div>
    </section>
  );
}

function Row({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-center gap-4">{children}</div>;
}

function Label({ children }: { children: ReactNode }) {
  return <p className="mb-2 text-xs font-medium uppercase tracking-wide text-text-secondary">{children}</p>;
}

const iconColors: IconCircleColor[] = ['primary', 'orange', 'green', 'red', 'neutral'];

/**
 * Every token and component in one page, for comparing against the source
 * mockup before real screens get built on top of them. Rendered by both
 * apps at /design-system (see their App.tsx) - lives here, not duplicated
 * per app, same as everything else in this package.
 */
export function DesignSystemPreview() {
  return (
    <div className="mx-auto max-w-3xl px-screen py-10">
      <header className="mb-12">
        <p className="font-heading text-2xl font-bold text-primary">SheOut Design System</p>
        <p className="mt-1 text-sm text-text-secondary">
          Tokens and components extracted from the mockup - review against the original before building real screens
          on top of these.
        </p>
      </header>

      <Section title="Colors" description="Every hex value here is eyeballed from the mockup image - double-check against source files if available.">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          {Object.entries(tokens.colors).map(([name, value]) => (
            <div key={name} className="flex items-center gap-3">
              <span
                className="h-10 w-10 shrink-0 rounded-input border border-border"
                style={{ backgroundColor: value }}
              />
              <span>
                <span className="block text-sm font-medium text-text-primary">{name}</span>
                <span className="block text-xs text-text-secondary">{value}</span>
              </span>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Typography" description="Poppins for headings/logo, Inter for body text - closest Google Font match, not confirmed against source files.">
        <div className="space-y-3">
          <p className="font-heading text-3xl font-bold text-text-primary">Poppins Bold 800 - SHEOUT</p>
          <p className="font-heading text-xl font-semibold text-text-primary">Poppins Semibold - Hello, Priya</p>
          <p className="font-sans text-base text-text-primary">Inter Regular - Home / Bookings / Wallet / Profile</p>
          <p className="font-sans text-sm text-text-secondary">Inter Regular, secondary color - Safe · Fast · Women Focused</p>
        </div>
      </Section>

      <Section title="Radius scale">
        <Row>
          <div>
            <Label>rounded-card (20px)</Label>
            <div className="h-16 w-24 rounded-card bg-primary-light" />
          </div>
          <div>
            <Label>rounded-input (14px)</Label>
            <div className="h-16 w-24 rounded-input bg-primary-light" />
          </div>
          <div>
            <Label>rounded-chip (10px)</Label>
            <div className="h-10 w-24 rounded-chip bg-primary-light" />
          </div>
          <div>
            <Label>rounded-full (pill / circle)</Label>
            <div className="h-10 w-24 rounded-full bg-primary-light" />
          </div>
        </Row>
      </Section>

      <Section title="Shadows">
        <Row>
          <div>
            <Label>shadow-card</Label>
            <div className="h-16 w-24 rounded-card bg-surface shadow-card" />
          </div>
          <div>
            <Label>shadow-raised</Label>
            <div className="h-16 w-24 rounded-card bg-primary shadow-raised" />
          </div>
        </Row>
      </Section>

      <Section title="Button">
        <div className="space-y-3">
          <Row>
            <Button variant="primary">Book Now</Button>
            <Button variant="secondary">Continue with Google</Button>
            <Button variant="danger">Cancel Ride</Button>
            <Button variant="success">Accept</Button>
          </Row>
          <Row>
            <Button variant="primary" size="md">
              Medium
            </Button>
            <Button variant="primary" disabled>
              Disabled
            </Button>
            <Button variant="primary" fullWidth>
              Full width
            </Button>
          </Row>
        </div>
      </Section>

      <Section title="Card">
        <Row>
          <Card className="w-64">
            <p className="font-heading font-semibold text-text-primary">Surface card</p>
            <p className="mt-1 text-sm text-text-secondary">Default white card with shadow-card.</p>
          </Card>
          <Card variant="primary" className="w-64">
            <p className="font-heading font-semibold">Ride with confidence</p>
            <p className="mt-1 text-sm opacity-90">Primary gradient card for banners / balance.</p>
          </Card>
        </Row>
      </Section>

      <Section title="IconCircle">
        <div className="space-y-4">
          <div>
            <Label>Solid (category icons)</Label>
            <Row>
              {iconColors.map((c) => (
                <IconCircle key={c} color={c} tone="solid" icon={<Bike />} />
              ))}
            </Row>
          </div>
          <div>
            <Label>Soft (wallet actions / safety checks)</Label>
            <Row>
              {iconColors.map((c) => (
                <IconCircle key={c} color={c} tone="soft" icon={<CheckCircle2 />} />
              ))}
            </Row>
          </div>
          <div>
            <Label>Sizes</Label>
            <Row>
              <IconCircle size="sm" icon={<Bike />} />
              <IconCircle size="md" icon={<Bike />} />
              <IconCircle size="lg" icon={<Bike />} />
            </Row>
          </div>
        </div>
      </Section>

      <Section title="TopHeader">
        <div className="space-y-4">
          <Card>
            <TopHeader variant="back" title="Bike Taxi" />
          </Card>
          <Card>
            <TopHeader variant="greeting" title="Hello, Priya 👋" subtitle="Your safety, our priority" />
          </Card>
        </div>
      </Section>

      <Section title="ListRow">
        <div className="space-y-4">
          <Card className="divide-y divide-border p-0">
            <div className="p-4">
              <ListRow
                icon={<IconCircle tone="soft" size="sm" icon={<MapPin />} />}
                label="Pickup Location"
                sublabel="Your Current Location"
              />
            </div>
            <div className="p-4">
              <ListRow
                icon={<IconCircle tone="soft" color="orange" size="sm" icon={<MapPin />} />}
                label="Drop Location"
                sublabel="Select Destination"
              />
            </div>
          </Card>
          <Card>
            <Label>Stacked layout (wallet quick actions)</Label>
            <div className="flex justify-around">
              <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<PlusCircle />} />} label="Add Money" />
              <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<Send />} />} label="Send Money" />
              <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<Receipt />} />} label="Transactions" />
            </div>
          </Card>
        </div>
      </Section>

      <Section title="StatusBadge">
        <Row>
          <StatusBadge tone="success">Completed</StatusBadge>
          <StatusBadge tone="success">Delivered</StatusBadge>
          <StatusBadge tone="warning">Pending</StatusBadge>
          <StatusBadge tone="danger">Cancelled</StatusBadge>
          <StatusBadge tone="primary">Active</StatusBadge>
        </Row>
      </Section>

      <Section title="AmountText">
        <Row>
          <AmountText amount={1250} sign="neutral" size="lg" />
          <AmountText amount={120} sign="positive" />
          <AmountText amount={56} sign="negative" />
        </Row>
      </Section>

      <Section title="BottomNavBar" description="Includes the raised center item used for the customer app's SOS tab.">
        <div className="mx-auto max-w-sm overflow-hidden rounded-card shadow-card">
          <BottomNavBar
            items={[
              { key: 'home', label: 'Home', icon: <Home />, active: true },
              { key: 'bookings', label: 'Bookings', icon: <Calendar /> },
              { key: 'sos', label: 'SOS', icon: <Siren />, raised: true },
              { key: 'wallet', label: 'Wallet', icon: <WalletIcon /> },
              { key: 'profile', label: 'Profile', icon: <User /> },
            ]}
          />
        </div>
      </Section>

      <Section title="Category icons reference" description="How IconCircle + ListRow(stacked) combine for the Home screen's Quick Access row.">
        <Card>
          <div className="flex justify-around">
            <ListRow layout="stacked" icon={<IconCircle icon={<Bike />} />} label="Bike Taxi" />
            <ListRow layout="stacked" icon={<IconCircle color="orange" icon={<Package />} />} label="Parcel" />
            <ListRow layout="stacked" icon={<IconCircle color="green" icon={<UtensilsCrossed />} />} label="Lunch Box" />
          </div>
        </Card>
      </Section>

      <div className="flex items-center gap-2 text-xs text-text-secondary">
        <BellIcon className="h-4 w-4" />
        <ChevronRight className="h-4 w-4" />
        <span>Icons via lucide-react - not part of the original mockup's icon set, closest visual match.</span>
      </div>
    </div>
  );
}
