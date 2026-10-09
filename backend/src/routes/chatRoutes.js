const express = require('express');
const router = express.Router();
const chatController = require('../controllers/chatController');
const authenticateToken = require('../middleware/auth');

router.use(authenticateToken);

// List circle contacts & conversations
router.get('/contacts', chatController.getContacts);

// Quick care item catalog for in-chat sharing
router.get('/quick-items', chatController.getQuickCareItems);

// Message thread for a specific connection
router.get('/messages/:connection_id', chatController.getMessages);

// Send text message
router.post('/send', chatController.sendMessage);

// Send or request a care / medical item in chat
router.post('/send-item', chatController.sendCareItem);

module.exports = router;
