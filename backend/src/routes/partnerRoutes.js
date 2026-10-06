const express = require('express');
const router = express.Router();
const partnerController = require('../controllers/partnerController');
const authenticateToken = require('../middleware/auth');

// Public invite validation
router.get('/invite/:token', partnerController.validateInvite);

// All other partner routes require authentication
router.use(authenticateToken);

// Search & Invites
router.get('/search', partnerController.searchPartner);
router.post('/invite', partnerController.createInvite);

// Connections Lifecycle
router.post('/request', partnerController.sendConnectionRequest);
router.get('/requests', partnerController.listRequests);
router.post('/requests/:id/accept', partnerController.acceptRequest);
router.post('/requests/:id/decline', partnerController.declineRequest);
router.delete('/:connectionId', partnerController.revokeConnection);
router.post('/:connectionId/block', partnerController.blockPartner);

// Granular Permissions Management
router.get('/:connectionId/permissions', partnerController.getPermissions);
router.put('/:connectionId/permissions', partnerController.updatePermissions);

// Permitted Data Access (Server-side enforced)
router.get('/:connectionId/shared-cycle', partnerController.getSharedCycle);
router.get('/:connectionId/care-kit', partnerController.getSharedCareKit);
router.get('/:connectionId/wishlist', partnerController.getSharedWishlist);
router.get('/:connectionId/shared-address', partnerController.getSharedAddress);

// Care Package Order
router.post('/:connectionId/care-package', partnerController.createCarePackage);

module.exports = router;
