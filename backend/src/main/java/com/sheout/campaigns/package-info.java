/**
 * Campaigns: promotional credit for riders and incentives for partners.
 * <p>
 * A temporary layer on top of the real fare. Pricing is untouched: a trip
 * is priced exactly as it would be without any campaign, and a promotion
 * then pays some or all of it on the rider's behalf; an incentive adds to
 * what a partner earns, out of SheOut's margin, never out of the rider's
 * fare or the partner's share. Every campaign has a budget cap and stops by
 * itself when it is reached, and every one can be paused or changed in the
 * console without a deploy.
 * <p>
 * Public: {@link com.sheout.campaigns.CampaignsApi} (booking asks it for a
 * discount), {@link com.sheout.campaigns.CampaignsAdminApi} (the console),
 * and the {@link com.sheout.campaigns.DriverIncentiveAwarded} event (payouts
 * credits the wallet from it). Everything else reacts to other modules'
 * events.
 */
package com.sheout.campaigns;
