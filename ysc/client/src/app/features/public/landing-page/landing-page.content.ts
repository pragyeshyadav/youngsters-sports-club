export interface LandingNavItem {
  readonly id: string;
  readonly label: string;
}

export interface LandingStat {
  readonly value: string;
  readonly label: string;
}

export interface LandingActivity {
  readonly icon: string;
  readonly title: string;
  readonly description: string;
  readonly imageUrl: string;
  readonly alt: string;
  readonly imagePosition?: string;
}

export interface LandingBranchFact {
  readonly label: string;
  readonly value: string;
}

export interface LandingBranch {
  readonly label: string;
  readonly city: string;
  readonly state?: string;
  readonly category?: string;
  readonly title: string;
  readonly description: string;
  readonly mapUrl: string;
  readonly phoneHref?: string;
  readonly callLabel?: string;
  readonly facts: readonly LandingBranchFact[];
  readonly highlights?: readonly string[];
}

export interface LandingGalleryItem {
  readonly id: string;
  readonly business: string;
  readonly location: string;
  readonly title: string;
  readonly caption: string;
  readonly imageUrl: string;
  readonly alt: string;
}

export interface LandingLink {
  readonly label: string;
  readonly href: string;
}

export interface LandingTestimonial {
  readonly business: string;
  readonly author: string;
  readonly ratingLabel?: string;
  readonly quote: string;
}

export interface TournamentResultLine {
  readonly label: string;
  readonly name: string;
}

export interface TournamentResultGroup {
  readonly heading?: string;
  readonly results: readonly TournamentResultLine[];
}

export interface TournamentCard {
  readonly sport: string;
  readonly icon: string;
  readonly note?: string;
  readonly groups: readonly TournamentResultGroup[];
}

export interface TournamentShowcase {
  readonly id: string;
  readonly title: string;
  readonly tagline: string;
  readonly summary: string;
  readonly sports: readonly string[];
  readonly closingNote: string;
  readonly featureImageUrl: string;
  readonly featureImageAlt: string;
  readonly pressImageUrl: string;
  readonly pressImageAlt: string;
  readonly cards: readonly TournamentCard[];
}

export interface LandingCarouselImage {
  readonly id: string;
  readonly imageUrl: string;
  readonly alt: string;
  readonly caption: string;
}

export interface CricketAcademyShowcase {
  readonly id: string;
  readonly title: string;
  readonly tagline: string;
  readonly summary: string;
  readonly coachName: string;
  readonly coachHighlight: string;
  readonly achievements: readonly string[];
  readonly closingNote: string;
  readonly images: readonly LandingCarouselImage[];
}

export interface KidsOceanShowcase {
  readonly id: string;
  readonly title: string;
  readonly tagline: string;
  readonly summary: string;
  readonly actions: readonly LandingLink[];
  readonly images: readonly LandingCarouselImage[];
}

export interface FounderShowcase {
  readonly id: string;
  readonly title: string;
  readonly name: string;
  readonly role: string;
  readonly credentials: string;
  readonly quote: string;
  readonly story: readonly string[];
  readonly images: readonly LandingCarouselImage[];
}

export interface ClubManagerProfile {
  readonly id: string;
  readonly name: string;
  readonly role: string;
  readonly summary: string;
  readonly imageUrl: string;
  readonly alt: string;
}

export interface UpcomingEventFact {
  readonly label: string;
  readonly value: string;
}

export interface UpcomingEventShowcase {
  readonly id: string;
  readonly title: string;
  readonly tagline: string;
  readonly summary: string;
  readonly posterUrl: string;
  readonly posterAlt: string;
  readonly sports: readonly string[];
  readonly facts: readonly UpcomingEventFact[];
  readonly location: string;
  readonly contactLabel: string;
  readonly contactHref: string;
 }

export const LANDING_NAV_ITEMS: readonly LandingNavItem[] = [
  { id: 'home', label: 'Home' },
  { id: 'activities', label: 'Activities' },
  { id: 'moments', label: 'Moments' },
  { id: 'branches', label: 'Branches' },
  { id: 'kids-ocean', label: 'Kids Ocean Dreamland' },
  { id: 'contact', label: 'Contact' },
];

export const LANDING_STATS: readonly LandingStat[] = [
  { value: '4.9 / 5', label: 'Google rating surfaced for Youngsters Sports Club & Cafe' },
  { value: '10 AM – 10 PM', label: 'Published daily hours for the Satna club listing' },
  { value: '5', label: 'Winter Olympics 2K25 sports highlighted on this page' },
];

export const UPCOMING_VINDHYA_OLYMPICS_2K26: UpcomingEventShowcase = {
  id: 'upcoming-event',
  title: 'Upcoming Event',
  tagline: 'Vindhya Olympics 2K26 starts from 30th August 2026.',
  summary:
    'Youngsters Sports Club & Cafe Satna is set to host Vindhya Olympics 2K26 with five headline tournaments, a ₹10,000 winner prize pool and a direct registration path for players who want to be part of the next big competition.',
  posterUrl: '/images/landing/events/vindhya-olympics-2k26-poster.png',
  posterAlt: 'Vindhya Olympics 2K26 poster by Youngsters Sports Club & Cafe Satna',
  sports: ['Table Tennis', 'Snooker', '8 Ball Pool', 'Chess', 'Carrom'],
  facts: [
    { label: 'Tournament starts', value: 'Sunday, 30th August 2026' },
    { label: 'Grand finale', value: '27th September 2026 (Tentative)' },
    { label: 'Registration fee', value: '₹200 only' },
    { label: 'Winner prize pool', value: '₹10,000 + trophy' },
  ],
  location: 'Youngsters Sports Club, behind Satna Marriage Garden, Utaili, Satna (M.P.)',
  contactLabel: '+91 97656 57902',
  contactHref: 'tel:+919765657902',
};

export const LANDING_ACTIVITIES: readonly LandingActivity[] = [
  {
    icon: '🎱',
    title: 'Snooker',
    description: 'Real cue-sport atmosphere, match play and championship moments.',
    imageUrl: '/images/landing/activities/snooker.jpg',
    alt: 'Snooker action at Youngsters Sports Club Satna',
  },
  {
    icon: '🎱',
    title: '8 Ball Pool',
    description: 'Featured as part of Winter Olympics 2K25 and club tournament energy.',
    imageUrl: '/images/landing/activities/pool.jpg',
    alt: '8 ball pool action at Youngsters Sports Club Satna',
  },
  {
    icon: '🏓',
    title: 'Table Tennis',
    description: 'Fast-paced indoor play highlighted across the event coverage shared here.',
    imageUrl: '/images/landing/activities/table-tennis.jpg',
    alt: 'Table tennis group moment at Youngsters Sports Club Satna',
  },
  {
    icon: '🎯',
    title: 'Carrom',
    description: 'Board-game competition and tournament spirit remain part of the indoor club mix.',
    imageUrl: '/images/landing/activities/carrom.jpg',
    alt: 'Carrom game in progress at Youngsters Sports Club',
  },
  {
    icon: '♟',
    title: 'Chess',
    description: 'Focused, face-to-face chess play adds a strategy corner to the club experience.',
    imageUrl: '/images/landing/activities/chess.jpg',
    alt: 'Chess game in progress at Youngsters Sports Club Satna',
  },
  {
    icon: '🎮',
    title: 'PS5',
    description: 'Console gaming brings quick multiplayer fun and screen-based play into the club lineup.',
    imageUrl: '/images/landing/activities/ps5.jpg',
    alt: 'PS5 controller and screen setup at Youngsters Sports Club',
  },
  {
    icon: '🥽',
    title: 'VR Games',
    description: 'Immersive virtual-reality play adds another high-energy indoor entertainment option.',
    imageUrl: '/images/landing/activities/vr-games.jpg',
    alt: 'Virtual reality gaming at Kids Ocean Dreamland',
    imagePosition: 'center 22%',
  },
  {
    icon: '🏏',
    title: 'Youngsters Cricket Academy',
    description: 'Outdoor cricket coaching adds academy-style training and team discipline to the wider YSC ecosystem.',
    imageUrl: '/images/landing/activities/cricket-academy.jpg',
    alt: 'Youngsters Cricket Academy coaching session with players on the ground',
  },
  {
    icon: '🧒',
    title: 'Kids Play',
    description: 'Kids Ocean Dreamland adds family fun and celebration energy to the wider YSC ecosystem.',
    imageUrl: '/images/landing/activities/kids-play.jpg',
    alt: 'Kids Ocean Dreamland celebration with children and families',
  },
];

export const LANDING_BRANCHES: readonly LandingBranch[] = [
  {
    label: 'Youngsters Sports Club & Cafe',
    city: 'Satna',
    state: 'Madhya Pradesh',
    category: 'Cafe · Club',
    title: 'Youngsters Sports Club & Cafe',
    description: 'Satna club destination with verified public listing details and direct contact access.',
    mapUrl: 'https://maps.app.goo.gl/BcYn3kgfs1yXTf1LA',
    phoneHref: 'tel:+919765657902',
    callLabel: '+91 97656 57902',
    facts: [
      { label: 'Google rating', value: '4.9 / 5' },
      { label: 'Reviews surfaced', value: '76' },
      { label: 'Hours', value: 'Daily · 10 AM – 10 PM' },
      { label: 'Address', value: 'Utaili, behind Satna Marriage Garden, Satna, Madhya Pradesh' },
    ],
    highlights: ['Snooker', '8 Ball Pool', 'Table Tennis', 'Chess', 'Carrom'],
  },
  {
    label: 'Kids Ocean Dreamland',
    city: 'Satna',
    state: 'Madhya Pradesh',
    title: 'Kids Ocean Dreamland · Satna',
    description: 'Verified destination link available now. Real Kids Ocean imagery can be added in the next content pass.',
    mapUrl: 'https://maps.app.goo.gl/keJGzz3mPN86GoQG6',
    facts: [
      { label: 'Location link', value: 'Verified Google Maps destination provided' },
      { label: 'Brand', value: 'Kids Ocean Dreamland' },
      { label: 'Status', value: 'Photo update planned with supplied assets later' },
    ],
    highlights: ['Satna', 'Kids play', 'Family destination'],
  },
  {
    label: 'Kids Ocean Dreamland',
    city: 'Rewa',
    state: 'Madhya Pradesh',
    title: 'Kids Ocean Dreamland · Rewa',
    description: 'Verified destination link available now. Rewa-specific photos can be added as soon as you share them.',
    mapUrl: 'https://maps.app.goo.gl/M7PgqUnT8s8gr2X29',
    facts: [
      { label: 'Location link', value: 'Verified Google Maps destination provided' },
      { label: 'Brand', value: 'Kids Ocean Dreamland' },
      { label: 'Region', value: 'Rewa, Madhya Pradesh' },
    ],
    highlights: ['Rewa', 'Kids play', 'Branch discovery'],
  },
];

export const LANDING_GALLERY_ITEMS: readonly LandingGalleryItem[] = [
  {
    id: 'winter-hero',
    business: 'Youngsters Sports Club',
    location: 'Winter Olympics 2K25',
    title: 'Championship group moment',
    caption: 'Main Winter Olympics 2K25 moment from Youngsters Sports Club Satna.',
    imageUrl: '/images/landing/winter-olympics-2k25-hero.jpg',
    alt: 'Winter Olympics 2K25 group photograph at Youngsters Sports Club Satna',
  },
  {
    id: 'winter-award',
    business: 'Youngsters Sports Club',
    location: 'Snooker award moment',
    title: 'Winner recognition at the table',
    caption: 'A trophy handover moment captured during Winter Olympics 2K25.',
    imageUrl: '/images/landing/winter-olympics-2k25-award.jpg',
    alt: 'Snooker award presentation during Winter Olympics 2K25 at Youngsters Sports Club',
  },
  {
    id: 'winter-podium',
    business: 'Youngsters Sports Club',
    location: 'Award ceremony',
    title: 'Champions with trophies',
    caption: 'Another award ceremony moment showing the live club atmosphere and celebration.',
    imageUrl: '/images/landing/winter-olympics-2k25-podium.jpg',
    alt: 'Champions and organizers during Winter Olympics 2K25 at Youngsters Sports Club',
  },
  {
    id: 'winter-press',
    business: 'Youngsters Sports Club',
    location: 'Press coverage',
    title: 'Winter Olympics 2K25 in print',
    caption: 'Official press clipping shared by the club for Winter Olympics 2K25.',
    imageUrl: '/images/landing/winter-olympics-2k25-press.jpg',
    alt: 'Press clipping covering Winter Olympics 2K25 at Youngsters Sports Club',
  },
  {
    id: 'winter-trophy',
    business: 'Youngsters Sports Club',
    location: 'Trophy detail',
    title: 'Tournament trophy close-up',
    caption: 'A closer look at the Winter Olympics 2K25 championship trophy.',
    imageUrl: '/images/landing/winter-olympics-2k25-trophy.jpg',
    alt: 'Winter Olympics 2K25 snooker trophy at Youngsters Sports Club',
  },
];

export const LANDING_TESTIMONIALS: readonly LandingTestimonial[] = [
  {
    business: 'Youngsters Sports Club & Cafe',
    author: 'Google review highlight',
    ratingLabel: '4.9 / 5 public listing rating',
    quote: 'Guests repeatedly highlight the club atmosphere, clean setup and the excitement of playing here with friends.',
  },
  {
    business: 'Youngsters Sports Club & Cafe',
    author: 'Customer sentiment snapshot',
    ratingLabel: 'Snooker and indoor games',
    quote: 'Public feedback points to a welcoming sports spot for cue games, table tennis and real local competition energy.',
  },
  {
    business: 'Winter Olympics 2K25',
    author: 'Event showcase',
    ratingLabel: 'Owner-supplied tournament highlights',
    quote: 'Every shot, every frame and every comeback kept the audience on the edge of their seats.',
  },
];

export const WINTER_OLYMPICS_2K25: TournamentShowcase = {
  id: 'winter-olympics-2k25',
  title: 'Winter Olympics 2K25',
  tagline: '5 sports • champions • unforgettable moments',
  summary: 'A visual glimpse into the tournament season that brought snooker, 8 ball pool, table tennis, chess and carrom into one club-wide celebration.',
  sports: ['Snooker', '8 Ball Pool', 'Table Tennis', 'Chess', 'Carrom'],
  closingNote: 'Congratulations to all champions and finalists for their talent, focus, patience and sportsmanship.',
  featureImageUrl: '/images/landing/winter-olympics-2k25-hero.jpg',
  featureImageAlt: 'Winter Olympics 2K25 champions and organizers at Youngsters Sports Club Satna',
  pressImageUrl: '/images/landing/winter-olympics-2k25-press.jpg',
  pressImageAlt: 'Press coverage of Winter Olympics 2K25 at Youngsters Sports Club',
  cards: [
    {
      sport: 'Snooker',
      icon: '🎱',
      groups: [
        {
          heading: 'Professional',
          results: [
            { label: 'Winner', name: 'Rajesh Maghlani' },
            { label: 'Runner-up', name: 'Neeraj Soni' },
          ],
        },
        {
          heading: 'Amateur',
          results: [
            { label: 'Winner', name: 'Shubham Singh' },
            { label: 'Runner-up', name: 'Abdul' },
          ],
        },
      ],
    },
    {
      sport: '8 Ball Pool',
      icon: '🎱',
      note: 'Featured in Winter Olympics 2K25. Winner details were not supplied, so they are intentionally not shown here.',
      groups: [],
    },
    {
      sport: 'Table Tennis',
      icon: '🏓',
      groups: [
        {
          results: [
            { label: 'Winner', name: 'Achyut Mishra' },
            { label: 'Runner-up', name: 'Aryan Shukla' },
          ],
        },
      ],
    },
    {
      sport: 'Carrom',
      icon: '🎯',
      groups: [
        {
          results: [
            { label: 'Winner', name: 'Tanmay Gupta' },
            { label: 'Runner-up', name: 'Akarsh Gupta' },
          ],
        },
      ],
    },
    {
      sport: 'Chess Championship',
      icon: '♟',
      groups: [
        {
          results: [
            { label: 'Winner', name: 'Rudra Shukla' },
            { label: 'Runner-up', name: 'Arnav Jha' },
          ],
        },
      ],
    },
  ],
};

export const YOUNGSTERS_CRICKET_ACADEMY_SATNA: CricketAcademyShowcase = {
  id: 'cricket-academy',
  title: 'Youngsters Cricket Academy Satna',
  tagline: 'Cricket coaching, tournament moments and academy discipline from the YSC ecosystem.',
  summary:
    'A dedicated cricket section for Satna, highlighting coach Mr Rishabh Yadav and the real team moments shared from training and competition.',
  coachName: 'Mr Rishabh Yadav',
  coachHighlight:
    'Played in state and national level cricket championships and represented MP Rewa division at the broader level.',
  achievements: [
    'State-level championship experience',
    'National-level championship experience',
    'MP Rewa division representation',
    'Youngsters Cricket Academy Satna coaching focus',
  ],
  closingNote: 'Built to showcase real academy progress today, with room for more cricket seasons and achievements later.',
  images: [
    {
      id: 'cricket-team-award',
      imageUrl: '/images/landing/cricket-academy/cricket-academy-1.png',
      alt: 'Youngsters Cricket Academy Satna team group photograph with medals and guests',
      caption: 'Academy team moment with players, organizers and medals.',
    },
    {
      id: 'cricket-medal-group',
      imageUrl: '/images/landing/cricket-academy/cricket-academy-2.png',
      alt: 'Youngsters Cricket Academy Satna players posing with medals and a shield',
      caption: 'Players celebrating with medals and a tournament shield.',
    },
    {
      id: 'cricket-runner-up',
      imageUrl: '/images/landing/cricket-academy/cricket-academy-3.png',
      alt: 'Youngsters Cricket Academy Satna team at a cricket tournament podium with runner-up board',
      caption: 'Tournament-stage moment with the academy squad in competition colors.',
    },
    {
      id: 'cricket-coach-trophy',
      imageUrl: '/images/landing/cricket-academy/cricket-academy-4.png',
      alt: 'Cricket coach Mr Rishabh Yadav holding a trophy and tournament board',
      caption: 'Coach Rishabh Yadav with trophy and tournament recognition.',
    },
    {
      id: 'cricket-team-portrait',
      imageUrl: '/images/landing/cricket-academy/cricket-academy-5.png',
      alt: 'Youngsters Cricket Academy Satna players and staff posing with a trophy',
      caption: 'Academy portrait with players, staff and trophy on display.',
    },
    {
      id: 'cricket-training-group',
      imageUrl: '/images/landing/cricket-academy/cricket-academy-6.png',
      alt: 'Youngsters Cricket Academy Satna group training photograph on the cricket ground',
      caption: 'On-ground academy training lineup with young cricketers.',
    },
    {
      id: 'cricket-stadium-group',
      imageUrl: '/images/landing/cricket-academy/cricket-academy-7.png',
      alt: 'Youngsters Cricket Academy Satna team photograph at a stadium ground',
      caption: 'Stadium group moment with the academy squad and coaches.',
    },
  ],
};

export const KIDS_OCEAN_DREAMLAND_SHOWCASE: KidsOceanShowcase = {
  id: 'kids-ocean',
  title: 'Kids Ocean Dreamland',
  tagline: 'Soft play, birthday moments and bright family energy from Satna and Rewa.',
  summary:
    'The Kids Ocean Dreamland section now leans on real photographs, so families can quickly scan the play area, celebration setup and colorful indoor experience.',
  actions: [
    { label: 'Open Satna', href: 'https://maps.app.goo.gl/keJGzz3mPN86GoQG6' },
    { label: 'Open Rewa', href: 'https://maps.app.goo.gl/M7PgqUnT8s8gr2X29' },
  ],
  images: [
    {
      id: 'kids-ball-pit',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-1.png',
      alt: 'Children playing in the colorful ball pit at Kids Ocean Dreamland',
      caption: 'Real ball-pit play moments inside Kids Ocean Dreamland.',
    },
    {
      id: 'kids-rides',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-2.png',
      alt: 'Children on ride-on toys at Kids Ocean Dreamland',
      caption: 'Toddler-friendly ride-on fun in the play zone.',
    },
    {
      id: 'kids-brand-wall',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-3.png',
      alt: 'Kids Ocean Dreamland interior branding wall and play area setup',
      caption: 'Interior branding and entry visuals from the play area.',
    },
    {
      id: 'kids-birthday',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-4.png',
      alt: 'Birthday celebration with children at Kids Ocean Dreamland',
      caption: 'Birthday celebrations are part of the family-focused experience.',
    },
    {
      id: 'kids-swing',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-5.png',
      alt: 'Child using the indoor swing at Kids Ocean Dreamland',
      caption: 'Soft-play movement zones designed for younger children.',
    },
    {
      id: 'kids-party-crowd',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-6.png',
      alt: 'Family gathering and cake-cutting event at Kids Ocean Dreamland',
      caption: 'Family gatherings and celebration moments inside the venue.',
    },
    {
      id: 'kids-family-selfie',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-7.png',
      alt: 'Family selfie inside Kids Ocean Dreamland play area',
      caption: 'Close, joyful family moments that reflect the play-zone atmosphere.',
    },
    {
      id: 'kids-neon-play-zone',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-8.png',
      alt: 'Illuminated soft play area with slides and ball pit at Kids Ocean Dreamland',
      caption: 'A brighter look at the slides, climbing area and ball pit setup.',
    },
    {
      id: 'kids-slides',
      imageUrl: '/images/landing/kids-ocean/kids-ocean-9.png',
      alt: 'Slides and ball pit setup inside Kids Ocean Dreamland',
      caption: 'Slides, tunnel entry and ball-pit views from another angle.',
    },
  ],
};

export const LANDING_FOUNDER_SHOWCASE: FounderShowcase = {
  id: 'founder',
  title: 'Meet the Founder',
  name: 'Pragyesh & Anushka Yadav',
  role: 'Founder — Youngsters Sports Club & Kids Ocean Dreamland',
  credentials: 'Software Engineer • Entrepreneur • Sports & Recreation Enthusiast',
  quote: 'Building spaces where people can play, compete, connect and create memories.',
  story: [
    'With a professional background in software engineering and a passion for building meaningful experiences, Pragyesh Yadav founded Youngsters Sports Club with a vision to create a modern destination for sports, gaming and community.',
    'That vision expanded with Kids Ocean Dreamland — creating a dedicated world of play and entertainment for children and families.',
    'Today, the journey continues across Satna and Rewa, bringing sports, technology, competition and family entertainment together under one growing ecosystem.',
  ],
  images: [
    {
      id: 'founder-pragyesh',
      imageUrl: '/images/landing/founder/pragyesh-yadav.png',
      alt: 'Pragyesh Yadav working at his desk with a laptop and headset',
      caption: 'Pragyesh Yadav',
    },
    {
      id: 'founder-anushka',
      imageUrl: '/images/landing/founder/anushka-yadav.png',
      alt: 'Anushka Yadav portrait in an office workspace',
      caption: 'Anushka Yadav',
    },
  ],
};

export const LANDING_CLUB_MANAGERS: readonly ClubManagerProfile[] = [
  {
    id: 'manager-satna',
    name: 'Prince Singh',
    role: 'Enthusiastic Snooker Champion · Manager — Satna',
    summary: 'Leading the Satna club floor with player-first energy, match-day support and a strong snooker presence.',
    imageUrl: '/images/landing/managers/prince-singh.png',
    alt: 'Prince Singh smiling beside a Royal Enfield bike outdoors',
  },
  {
    id: 'manager-rewa',
    name: 'Raghuwansh Yadav',
    role: 'RTO & Club Manager — Rewa',
    summary: 'Supporting the Rewa branch with steady operations, local leadership and day-to-day club coordination.',
    imageUrl: '/images/landing/managers/raghuwansh-yadav.png',
    alt: 'Raghuwansh Yadav portrait in formal attire at an event venue',
  },
];

export const LANDING_SOCIAL_LINKS: readonly LandingLink[] = [
  {
    label: 'Instagram',
    href: 'https://www.instagram.com/youngsters.sportsclub?igsh=bWliamh6dGJ6cGEw',
  },
  {
    label: 'Google Reviews',
    href: 'https://g.page/r/CTo24eMe382oEBM/review',
  },
];
