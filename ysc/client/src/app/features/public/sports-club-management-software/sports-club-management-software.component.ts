import { DOCUMENT } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

interface ProductFeature {
  readonly icon: string;
  readonly title: string;
  readonly description: string;
  readonly points: readonly string[];
}

interface ProductScreenshot {
  readonly title: string;
  readonly description: string;
  readonly image: string;
}

interface ProductFaq {
  readonly question: string;
  readonly answer: string;
}

@Component({
  selector: 'app-sports-club-management-software',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './sports-club-management-software.component.html',
  styleUrl: './sports-club-management-software.component.scss',
})
export class SportsClubManagementSoftwareComponent implements OnInit, OnDestroy {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly canonicalUrl = 'https://youngsterssportsclub.com/sports-club-management-software';
  private readonly whatsappUrl = 'https://wa.me/919765657902';
  private readonly schemaId = 'product-page-structured-data';

  readonly demoUrl = 'https://www.youtube.com/embed/w50KSLIlVzI';
  readonly safeDemoUrl: SafeResourceUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.demoUrl);
  readonly whatsappCta = this.whatsappUrl;
  readonly logoUrl = '/images/logo.png';

  readonly clubTypes = [
    ['8', 'Snooker Clubs', 'Manage tables, sessions, players, winners, frame history, billing and leaderboards.'],
    ['◉', 'Pool & Billiards Clubs', 'Keep tables, playing time, customers and payments organized in one operational view.'],
    ['🏓', 'Indoor Sports Clubs', 'Bring table tennis, carrom, chess and other activities into a connected club workflow.'],
    ['🎮', 'Gaming & PS5 Cafés', 'Track consoles, game sessions, duration-based billing, customers and payments.'],
    ['✦', 'Multi-Activity Clubs', 'Manage different games and activities without stitching together separate registers.'],
    ['⌂', 'Multi-Branch Businesses', 'Run multiple branches from one account with centralized visibility and branch-level operations.'],
  ] as const;

  readonly features: readonly ProductFeature[] = [
    { icon: '8', title: 'Game & Table Management', description: 'Know what is happening on the floor and keep sessions moving smoothly.', points: ['Table availability and active sessions', 'Start/end sessions and duration tracking', 'Player selection, billing and winner tracking'] },
    { icon: '◎', title: 'Customer Management', description: 'Give every player a useful history instead of another paper entry.', points: ['Customer and player profiles', 'Visit and playing history', 'Balances, dues and engagement context'] },
    { icon: '₹', title: 'Billing & Payments', description: 'Make daily collections easier to understand and easier to reconcile.', points: ['Session billing and payment history', 'Outstanding dues and settlement visibility', 'Daily and monthly revenue tracking'] },
    { icon: '🏆', title: 'Leaderboards & Engagement', description: 'Turn regular play into a reason for customers to return.', points: ['Monthly wins and player rankings', 'Challenges, rewards and tournament engagement', 'Performance history for your club community'] },
    { icon: '⚙', title: 'Staff & Operations', description: 'Give your team the controls and information needed for daily operations.', points: ['Staff access and manager approvals', 'Operational controls and expense tracking', 'Role-based access where available'] },
    { icon: '⌂', title: 'Manage Multiple Clubs & Branches from One Account', description: 'Grow beyond one location without losing the details that keep each branch running well.', points: ['Central organization view', 'Branch-specific operations and reporting', 'Base branch and additional access for staff'] },
  ];

  readonly screenshots: readonly ProductScreenshot[] = [
    { title: 'Manager Portal', description: 'A single place for earnings, frames, customers, staff and daily operations.', image: '/images/product/manager-portal.jpeg' },
    { title: 'Today’s Earnings', description: 'See revenue, outstanding payments and pending customer dues.', image: '/images/product/today-earnings.jpeg' },
    { title: 'Start and End Snooker Frames', description: 'Select a table and players, then keep the live session record clear.', image: '/images/product/snooker-frames.jpeg' },
    { title: 'Ongoing Snooker Frames', description: 'See active tables, players, start time and controls at a glance.', image: '/images/product/ongoing-snooker-frame.jpeg' },
    { title: 'Settle Payments', description: 'Review due amounts, discounts, payment mode and remaining balance.', image: '/images/product/settle-payment.jpeg' },
    { title: 'Customer Game and Payment History', description: 'Give staff and players a clearer view of activity over time.', image: '/images/product/player-history.jpeg' },
    { title: 'Consumable Orders', description: 'Add food and consumable items to a customer order and track totals.', image: '/images/product/consumable-orders.jpeg' },
    { title: 'Play Zone Activities', description: 'Manage PS5, table tennis and other timed activities alongside the club.', image: '/images/product/play-zone-activities.jpeg' },
    { title: 'Monthly Table-wise Earnings', description: 'Understand how each table contributes to monthly performance.', image: '/images/product/monthly-table-earnings.jpeg' },
    { title: 'Monthly Expenses', description: 'Record and review operating expenses with the manager portal.', image: '/images/product/add-monthly-expenses.jpeg' },
    { title: 'Customer Dues', description: 'Review players and their outstanding balances in one list.', image: '/images/product/customer-dues.jpeg' },
    { title: 'WhatsApp Notifications', description: 'Send customer updates and offers through the existing notification workflow.', image: '/images/product/whatsapp-notifications.jpeg' },
  ];

  readonly faqs: readonly ProductFaq[] = [
    { question: 'What is sports club management software?', answer: 'Sports club management software brings daily club operations into one system. Youngsters Sports Club Management System helps teams manage game sessions, tables, customers, billing, payments, staff, leaderboards, expenses and branch operations from a web dashboard instead of disconnected registers and spreadsheets.' },
    { question: 'What is snooker club management software?', answer: 'Snooker club management software helps operators track table availability, start and end frames, select players, calculate session charges, record winners, settle payments and review player history. It gives staff a consistent operational record for the club while making customer and revenue information easier to find.' },
    { question: 'How can I manage snooker table playing time digitally?', answer: 'Staff can select an available table, start a frame, add the players and end the frame when play finishes. The system keeps the session timing and table state together, helping the club calculate charges and see which tables are currently occupied without relying on handwritten start and end times.' },
    { question: 'Can the software automatically track game duration?', answer: 'Yes. Session-based activities can record start and end times and use the duration in the club’s existing billing workflow. This is useful for snooker, pool, PS5 and other timed indoor activities where accurate playing time is important for consistent customer billing.' },
    { question: 'Can I manage multiple sports club branches?', answer: 'Yes. The platform is designed for organizations with one or more branches. Owners can keep a central view while staff work with the branch-specific tables, customers, sessions, payments and reports they are permitted to access.' },
    { question: 'Can I track player wins and leaderboards?', answer: 'Yes. The system can surface player performance such as wins and rankings for supported club activities. Leaderboards give regular players a reason to return and help clubs build a more engaging community around ongoing games and tournaments.' },
    { question: 'Can I manage snooker, pool, PS5 and table tennis in the same system?', answer: 'The platform is built for clubs that run more than one activity. Alongside snooker operations, it can support pool and billiards, gaming and PS5, table tennis, VR, chess, carrom and other configured activities according to the club’s setup.' },
    { question: 'Is Youngsters Sports Club Management System suitable for Indian sports clubs?', answer: 'It was built from real Youngsters Sports Club operations and is designed around practical club workflows such as Indian Rupee billing, customer dues, staff operations, WhatsApp communication and branch management. Owners can contact the team to discuss whether the current setup fits their club.' },
    { question: 'Can staff and managers use the software?', answer: 'Yes. Staff and managers can use the operational areas assigned to them, while role-based access helps keep administrative controls with the appropriate people. The exact experience depends on the organization and branch access configured for each team member.' },
    { question: 'Can I view customer playing and payment history?', answer: 'Yes. Customer profiles can connect playing activity, visits, payments and outstanding dues so staff have useful context when helping a player. This makes it easier to answer questions, settle balances and understand how customers use the club over time.' },
  ];

  protected readonly openFaq = new Set<number>();

  ngOnInit(): void {
    this.title.setTitle('Sports Club Management Software for Snooker, Pool & Gaming Clubs | Youngsters');
    this.meta.addTags([
      { name: 'description', content: 'Youngsters Sports Club Management System helps snooker, pool, gaming and indoor sports clubs manage tables, game sessions, customers, payments, leaderboards, staff and multiple branches.' },
      { name: 'robots', content: 'index,follow' },
      { property: 'og:title', content: 'Sports Club Management Software for Snooker, Pool & Gaming Clubs | Youngsters' },
      { property: 'og:description', content: 'Manage tables, game sessions, customers, payments, staff, leaderboards and multiple branches from one sports club management platform.' },
      { property: 'og:url', content: this.canonicalUrl },
      { property: 'og:type', content: 'website' },
      { property: 'og:image', content: `${this.canonicalUrl.replace('/sports-club-management-software', '')}${this.logoUrl}` },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: 'Sports Club Management Software | Youngsters' },
      { name: 'twitter:description', content: 'Software for managing snooker, pool, gaming and indoor sports clubs.' },
    ]);
    this.addCanonicalLink();
    this.addStructuredData();
  }

  ngOnDestroy(): void {
    this.meta.removeTag('name="description"');
    this.meta.removeTag('name="robots"');
    ['og:title', 'og:description', 'og:url', 'og:type', 'og:image', 'twitter:card', 'twitter:title', 'twitter:description'].forEach((property) => {
      this.meta.removeTag(`property="${property}"`);
      this.meta.removeTag(`name="${property}"`);
    });
    this.document.querySelector('link[data-product-canonical="true"]')?.remove();
    this.document.getElementById(this.schemaId)?.remove();
  }

  protected toggleFaq(index: number): void {
    if (this.openFaq.has(index)) this.openFaq.delete(index); else this.openFaq.add(index);
  }

  protected isFaqOpen(index: number): boolean { return this.openFaq.has(index); }

  private addCanonicalLink(): void {
    const link = this.document.createElement('link');
    link.rel = 'canonical'; link.href = this.canonicalUrl; link.dataset['productCanonical'] = 'true';
    this.document.head.appendChild(link);
  }

  private addStructuredData(): void {
    const script = this.document.createElement('script');
    script.id = this.schemaId; script.type = 'application/ld+json';
    script.text = JSON.stringify({
      '@context': 'https://schema.org',
      '@graph': [
        { '@type': 'SoftwareApplication', name: 'Youngsters Sports Club Management System', applicationCategory: 'BusinessApplication', operatingSystem: 'Web', url: this.canonicalUrl, description: 'Sports club management software for managing snooker, pool, gaming and indoor sports clubs.', featureList: ['Game session management', 'Table availability tracking', 'Customer management', 'Payment tracking', 'Player leaderboards', 'Staff management', 'Multi-branch management'] },
        { '@type': 'FAQPage', mainEntity: this.faqs.map((faq) => ({ '@type': 'Question', name: faq.question, acceptedAnswer: { '@type': 'Answer', text: faq.answer } })) },
      ],
    });
    this.document.head.appendChild(script);
  }
}
