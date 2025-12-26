// MongoDB Initialization Script
// This script runs when the MongoDB container is first created

// Switch to edgar database
db = db.getSiblingDB('edgar');

// Create application user
db.createUser({
  user: 'edgar4j',
  pwd: 'edgar4j_password',
  roles: [
    { role: 'readWrite', db: 'edgar' }
  ]
});

// Create collections with validation
db.createCollection('companies', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['cik', 'name'],
      properties: {
        cik: { bsonType: 'string', description: 'CIK number is required' },
        name: { bsonType: 'string', description: 'Company name is required' }
      }
    }
  }
});

db.createCollection('fillings', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['cik', 'accessionNumber'],
      properties: {
        cik: { bsonType: 'string' },
        accessionNumber: { bsonType: 'string' }
      }
    }
  }
});

db.createCollection('tickers');
db.createCollection('submissions');
db.createCollection('form4');
db.createCollection('download_jobs');
db.createCollection('search_history');
db.createCollection('app_settings');

// Create indexes for better query performance
// Companies indexes
db.companies.createIndex({ cik: 1 }, { unique: true });
db.companies.createIndex({ ticker: 1 });
db.companies.createIndex({ name: 'text' });

// Fillings indexes
db.fillings.createIndex({ accessionNumber: 1 }, { unique: true });
db.fillings.createIndex({ cik: 1 });
db.fillings.createIndex({ 'formType.number': 1 });
db.fillings.createIndex({ fillingDate: -1 });
db.fillings.createIndex({ cik: 1, 'formType.number': 1 });
db.fillings.createIndex(
  { company: 'text', primaryDocDescription: 'text' },
  { name: 'fillings_text_search' }
);

// Tickers indexes
db.tickers.createIndex({ code: 1 }, { unique: true });
db.tickers.createIndex({ cik: 1 });
db.tickers.createIndex({ exchange: 1 });
db.tickers.createIndex({ code: 'text', name: 'text' });

// Submissions indexes
db.submissions.createIndex({ cik: 1 }, { unique: true });

// Form4 indexes
db.form4.createIndex({ tradingSymbol: 1 });
db.form4.createIndex({ transactionDate: -1 });
db.form4.createIndex({ issuerCik: 1 });

// Download jobs indexes
db.download_jobs.createIndex({ status: 1 });
db.download_jobs.createIndex({ startedAt: -1 });
db.download_jobs.createIndex({ type: 1, status: 1 });

// Search history indexes
db.search_history.createIndex({ timestamp: -1 });
db.search_history.createIndex({ query: 'text' });

print('MongoDB initialization completed successfully!');
